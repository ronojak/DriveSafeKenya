import ipaddress
import logging

from django.conf import settings
from django.utils import timezone
from datetime import timedelta
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated, IsAdminUser
from rest_framework.response import Response
from rest_framework import status

from .models import Payment, PaymentPlan, Subscription, PaymentAuditLog
from .daraja import stk_push, stk_query, normalize_phone

SAFARICOM_IP_RANGES = [
    ipaddress.ip_network("196.201.214.0/24"),
    ipaddress.ip_network("196.201.212.0/22"),
]

logger = logging.getLogger(__name__)


def _get_client_ip(request):
    trusted_proxies = getattr(settings, "TRUSTED_PROXY_IPS", set())
    remote_addr = request.META.get("REMOTE_ADDR", "")
    if trusted_proxies and remote_addr in trusted_proxies:
        xff = request.META.get("HTTP_X_FORWARDED_FOR")
        if xff:
            return xff.split(",")[0].strip()
    return remote_addr


def _is_safaricom_ip(request):
    try:
        client_ip = ipaddress.ip_address(_get_client_ip(request))
        return any(client_ip in net for net in SAFARICOM_IP_RANGES)
    except ValueError:
        return False


def _verify_callback_secret(request):
    secret = getattr(settings, "MPESA_CALLBACK_SECRET", "")
    if not secret:
        return True
    import hmac
    header_val = request.headers.get("X-Mpesa-Callback-Secret", "")
    return hmac.compare_digest(secret, header_val)


def _grant_subscription(user, plan, payment):
    start = timezone.now()
    expiry = start + timedelta(days=plan.duration_days) if plan.duration_days else None
    Subscription.objects.update_or_create(
        user=user,
        defaults={
            "plan": plan,
            "payment": payment,
            "status": "active",
            "start_date": start,
            "expiry_date": expiry,
            "source": "mpesa",
        },
    )


def _log_audit(payment, event_type, old_status, new_status, message="", payload=None):
    PaymentAuditLog.objects.create(
        payment=payment,
        event_type=event_type,
        old_status=old_status,
        new_status=new_status,
        message=message,
        raw_payload=payload,
    )


# ---------- POST /api/payments/mpesa/stk-push ----------

@api_view(["POST"])
@permission_classes([IsAuthenticated])
def mpesa_stk_push_view(request):
    user = request.user
    phone_raw = (request.data or {}).get("phoneNumber", "")
    plan_code = (request.data or {}).get("planId", "")

    if not phone_raw or not plan_code:
        return Response(
            {"error": "phoneNumber and planId required"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    phone = normalize_phone(phone_raw)
    if not phone:
        return Response(
            {"error": "Invalid phone number format"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        plan = PaymentPlan.objects.get(code=plan_code, is_active=True)
    except PaymentPlan.DoesNotExist:
        return Response(
            {"error": "Invalid or inactive plan"}, status=status.HTTP_400_BAD_REQUEST
        )

    # Rate limit: no new STK push if one is pending within last 60 seconds
    recent = Payment.objects.filter(
        user=user,
        status="PENDING",
        created_at__gte=timezone.now() - timedelta(seconds=60),
    ).exists()
    if recent:
        return Response(
            {"error": "Please wait before trying again"},
            status=status.HTTP_429_TOO_MANY_REQUESTS,
        )

    payment = Payment.objects.create(
        user=user,
        plan=plan,
        provider="mpesa",
        status="PENDING",
        amount=plan.amount,
        phone_number=phone,
    )

    callback_url = settings.BASE_URL_PUBLIC.rstrip("/") + "/api/payments/mpesa/callback"

    try:
        daraja_resp = stk_push(
            phone_number=phone,
            amount=plan.amount,
            account_reference=settings.MPESA_ACCOUNT_REFERENCE,
            transaction_desc=settings.MPESA_TRANSACTION_DESC,
            callback_url=callback_url,
        )
    except Exception as e:
        logger.error("STK Push failed: %s", e)
        payment.status = "FAILED"
        payment.failure_reason = str(e)
        payment.save(update_fields=["status", "failure_reason"])
        _log_audit(payment, "stk_push_failed", "PENDING", "FAILED", str(e))
        return Response(
            {"error": "stk_push_failed", "message": "Could not initiate payment"},
            status=status.HTTP_502_BAD_GATEWAY,
        )

    payment.merchant_request_id = daraja_resp.get("MerchantRequestID", "")
    payment.checkout_request_id = daraja_resp.get("CheckoutRequestID", "")
    payment.save(
        update_fields=["merchant_request_id", "checkout_request_id"]
    )
    _log_audit(payment, "stk_push_sent", "", "PENDING", payload=daraja_resp)

    return Response(
        {
            "success": True,
            "paymentId": str(payment.id),
            "merchantRequestId": payment.merchant_request_id,
            "checkoutRequestId": payment.checkout_request_id,
            "status": "PENDING",
            "message": "STK Push sent. Please complete payment on your phone.",
        }
    )


# ---------- POST /api/payments/mpesa/callback ----------

@api_view(["POST"])
def mpesa_callback_view(request):
    if not _verify_callback_secret(request):
        logger.warning("Callback with invalid secret from: %s", _get_client_ip(request))
        return Response(status=status.HTTP_403_FORBIDDEN)

    if getattr(settings, "DARAJA_ENV", "sandbox") != "sandbox":
        if not _is_safaricom_ip(request):
            logger.warning("Callback from non-Safaricom IP: %s", _get_client_ip(request))
            return Response(status=status.HTTP_403_FORBIDDEN)

    body = request.data or {}
    callback = (body.get("Body") or {}).get("stkCallback") or {}
    merchant_request_id = callback.get("MerchantRequestID", "")
    checkout_request_id = callback.get("CheckoutRequestID", "")
    result_code = callback.get("ResultCode")
    result_desc = callback.get("ResultDesc", "")

    items = ((callback.get("CallbackMetadata") or {}).get("Item")) or []
    by_name = {
        i.get("Name"): i.get("Value") for i in items if isinstance(i, dict)
    }
    mpesa_receipt = by_name.get("MpesaReceiptNumber") or by_name.get("MpesaReceipt")
    amount = by_name.get("Amount")
    phone = by_name.get("PhoneNumber") or by_name.get("MSISDN")

    # Find payment
    qs = Payment.objects.all()
    if checkout_request_id:
        qs = qs.filter(checkout_request_id=checkout_request_id)
    elif merchant_request_id:
        qs = qs.filter(merchant_request_id=merchant_request_id)
    else:
        return Response({"ResultCode": 0, "ResultDesc": "Accepted"})

    payment = qs.order_by("-created_at").first()
    if not payment:
        return Response({"ResultCode": 0, "ResultDesc": "Accepted"})

    # Idempotency: don't re-process already completed payments
    if payment.status in ("PAID", "FAILED", "CANCELLED"):
        _log_audit(
            payment, "duplicate_callback", payment.status, payment.status,
            "Duplicate callback ignored", body,
        )
        return Response({"ResultCode": 0, "ResultDesc": "Accepted"})

    old_status = payment.status
    payment.raw_callback = body
    payment.result_code = result_code
    payment.result_description = result_desc

    if result_code == 0:
        payment.status = "PAID"
        payment.mpesa_receipt_number = str(mpesa_receipt or "")
        payment.amount = int(amount or payment.amount)
        payment.phone_number = str(phone or payment.phone_number)
        payment.paid_at = timezone.now()
        payment.save()

        _log_audit(payment, "callback_success", old_status, "PAID", payload=body)

        if payment.plan:
            _grant_subscription(payment.user, payment.plan, payment)
    else:
        if "cancel" in result_desc.lower():
            payment.status = "CANCELLED"
        else:
            payment.status = "FAILED"
        payment.failure_reason = result_desc
        payment.save()
        _log_audit(
            payment, "callback_failed", old_status, payment.status,
            result_desc, body,
        )

    return Response({"ResultCode": 0, "ResultDesc": "Accepted"})


# ---------- GET /api/payments/status ----------

@api_view(["GET"])
@permission_classes([IsAuthenticated])
def payment_status_view(request):
    payment_id = request.query_params.get("paymentId")
    if not payment_id:
        return Response(
            {"error": "paymentId required"}, status=status.HTTP_400_BAD_REQUEST
        )

    try:
        payment = Payment.objects.get(id=payment_id, user=request.user)
    except Payment.DoesNotExist:
        return Response(
            {"error": "Payment not found"}, status=status.HTTP_404_NOT_FOUND
        )

    if payment.status == "PENDING" and payment.checkout_request_id:
        try:
            q = stk_query(payment.checkout_request_id)
            result_code = q.get("ResultCode")
            if result_code is not None:
                result_code = int(result_code)
            result_desc = q.get("ResultDesc", "")

            if result_code == 0:
                payment.status = "PAID"
                payment.result_code = result_code
                payment.paid_at = timezone.now()
                payment.save(update_fields=["status", "result_code", "paid_at"])
                _log_audit(payment, "query_success", "PENDING", "PAID", payload=q)
                if payment.plan:
                    _grant_subscription(payment.user, payment.plan, payment)
            elif result_code in (1032, 1037):
                payment.status = "CANCELLED"
                payment.result_code = result_code
                payment.failure_reason = result_desc
                payment.save(update_fields=["status", "result_code", "failure_reason"])
                _log_audit(payment, "query_cancelled", "PENDING", "CANCELLED", result_desc, q)
            elif result_code in (1, 2001, 1019, 1001):
                payment.status = "FAILED"
                payment.result_code = result_code
                payment.failure_reason = result_desc
                payment.save(update_fields=["status", "result_code", "failure_reason"])
                _log_audit(payment, "query_failed", "PENDING", "FAILED", result_desc, q)
        except Exception as e:
            logger.debug("STK query for payment %s: %s", payment_id, e)

    sub = Subscription.objects.filter(user=request.user).first()

    return Response(
        {
            "paymentId": str(payment.id),
            "status": payment.status,
            "planId": payment.plan.code if payment.plan else None,
            "amount": payment.amount,
            "receiptNumber": payment.mpesa_receipt_number or None,
            "accessActive": sub.status == "active" if sub else False,
            "expiryDate": sub.expiry_date.isoformat() if sub and sub.expiry_date else None,
        }
    )


# ---------- GET /api/payments/plans ----------

@api_view(["GET"])
def payment_plans_view(request):
    plans = PaymentPlan.objects.filter(is_active=True).order_by("amount")
    return Response(
        [
            {
                "code": p.code,
                "name": p.name,
                "amount": p.amount,
                "durationDays": p.duration_days,
                "description": p.description,
            }
            for p in plans
        ]
    )


# ---------- GET /api/payments/subscription ----------

@api_view(["GET"])
@permission_classes([IsAuthenticated])
def subscription_status_view(request):
    sub = Subscription.objects.filter(user=request.user).first()
    if not sub:
        return Response({"active": False, "plan": None, "expiryDate": None})

    # Auto-expire
    if sub.expiry_date and sub.expiry_date < timezone.now() and sub.status == "active":
        sub.status = "expired"
        sub.save(update_fields=["status"])

    return Response(
        {
            "active": sub.status == "active",
            "plan": sub.plan.code if sub.plan else None,
            "expiryDate": sub.expiry_date.isoformat() if sub.expiry_date else None,
            "startDate": sub.start_date.isoformat() if sub.start_date else None,
        }
    )


# ---------- Admin endpoints ----------

@api_view(["GET"])
@permission_classes([IsAdminUser])
def admin_payments_list_view(request):
    qs = Payment.objects.select_related("user", "plan").order_by("-created_at")

    status_filter = request.query_params.get("status")
    if status_filter:
        qs = qs.filter(status=status_filter)

    phone_filter = request.query_params.get("phone")
    if phone_filter:
        qs = qs.filter(phone_number__contains=phone_filter)

    receipt_filter = request.query_params.get("receipt")
    if receipt_filter:
        qs = qs.filter(mpesa_receipt_number__contains=receipt_filter)

    payments = qs[:100]
    return Response(
        [
            {
                "id": p.id,
                "user": p.user.email,
                "plan": p.plan.code if p.plan else None,
                "amount": p.amount,
                "phone": p.phone_number,
                "status": p.status,
                "receipt": p.mpesa_receipt_number,
                "paidAt": p.paid_at.isoformat() if p.paid_at else None,
                "createdAt": p.created_at.isoformat(),
            }
            for p in payments
        ]
    )


@api_view(["POST"])
@permission_classes([IsAdminUser])
def admin_manual_approval_view(request):
    payment_id = (request.data or {}).get("paymentId")
    receipt = (request.data or {}).get("receiptNumber", "")
    note = (request.data or {}).get("note", "")

    if not payment_id:
        return Response(
            {"error": "paymentId required"}, status=status.HTTP_400_BAD_REQUEST
        )

    try:
        payment = Payment.objects.get(id=payment_id)
    except Payment.DoesNotExist:
        return Response(
            {"error": "Payment not found"}, status=status.HTTP_404_NOT_FOUND
        )

    old_status = payment.status
    payment.status = "PAID"
    payment.mpesa_receipt_number = receipt
    payment.paid_at = timezone.now()
    payment.save(update_fields=["status", "mpesa_receipt_number", "paid_at"])

    _log_audit(
        payment, "manual_approval", old_status, "PAID",
        f"Admin: {request.user.email}. Note: {note}",
    )

    if payment.plan:
        _grant_subscription(payment.user, payment.plan, payment)

    return Response({"success": True, "paymentId": str(payment.id), "status": "PAID"})
