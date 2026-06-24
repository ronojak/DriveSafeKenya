import base64
import datetime as dt

import requests
from django.conf import settings


def _get_base_url():
    if settings.DARAJA_ENV == "production":
        return "https://api.safaricom.co.ke"
    return "https://sandbox.safaricom.co.ke"


def _token():
    if not settings.DARAJA_CONSUMER_KEY or not settings.DARAJA_CONSUMER_SECRET:
        raise RuntimeError("Daraja credentials not configured")
    auth = base64.b64encode(
        f"{settings.DARAJA_CONSUMER_KEY}:{settings.DARAJA_CONSUMER_SECRET}".encode()
    ).decode()
    url = f"{_get_base_url()}/oauth/v1/generate?grant_type=client_credentials"
    r = requests.get(url, headers={"Authorization": f"Basic {auth}"}, timeout=20)
    r.raise_for_status()
    return r.json()["access_token"]


def stk_push(
    *,
    phone_number: str,
    amount: int,
    account_reference: str,
    transaction_desc: str,
    callback_url: str,
):
    token = _token()
    ts = dt.datetime.now().strftime("%Y%m%d%H%M%S")
    password = base64.b64encode(
        f"{settings.DARAJA_SHORTCODE}{settings.DARAJA_PASSKEY}{ts}".encode()
    ).decode()

    url = f"{_get_base_url()}/mpesa/stkpush/v1/processrequest"

    payload = {
        "BusinessShortCode": settings.DARAJA_SHORTCODE,
        "Password": password,
        "Timestamp": ts,
        "TransactionType": settings.DARAJA_TRANSACTION_TYPE,
        "Amount": int(amount),
        "PartyA": str(phone_number),
        "PartyB": settings.DARAJA_SHORTCODE,
        "PhoneNumber": str(phone_number),
        "CallBackURL": callback_url,
        "AccountReference": account_reference,
        "TransactionDesc": transaction_desc,
    }
    r = requests.post(
        url, json=payload, headers={"Authorization": f"Bearer {token}"}, timeout=30
    )
    r.raise_for_status()
    return r.json()


def stk_query(checkout_request_id: str) -> dict:
    token = _token()
    ts = dt.datetime.now().strftime("%Y%m%d%H%M%S")
    password = base64.b64encode(
        f"{settings.DARAJA_SHORTCODE}{settings.DARAJA_PASSKEY}{ts}".encode()
    ).decode()

    url = f"{_get_base_url()}/mpesa/stkpushquery/v1/query"
    payload = {
        "BusinessShortCode": settings.DARAJA_SHORTCODE,
        "Password": password,
        "Timestamp": ts,
        "CheckoutRequestID": checkout_request_id,
    }
    r = requests.post(
        url, json=payload, headers={"Authorization": f"Bearer {token}"}, timeout=15
    )
    r.raise_for_status()
    return r.json()


def normalize_phone(raw: str) -> str | None:
    s = "".join(c for c in raw if c.isdigit())
    if s.startswith("2547") and len(s) == 12:
        return s
    if s.startswith("07") and len(s) == 10:
        return "254" + s[1:]
    if s.startswith("7") and len(s) == 9:
        return "254" + s
    if s.startswith("01") and len(s) == 10:
        return "254" + s[1:]
    if s.startswith("2541") and len(s) == 12:
        return s
    return None
