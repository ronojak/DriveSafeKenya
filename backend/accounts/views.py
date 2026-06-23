from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status
from rest_framework_simplejwt.tokens import RefreshToken

from .models import User
from .serializers import RegisterSerializer, UserSerializer


def _token_for_user(user):
    refresh = RefreshToken.for_user(user)
    return str(refresh.access_token)


@api_view(["POST"])
def register(request):
    ser = RegisterSerializer(data=request.data or {})
    ser.is_valid(raise_exception=True)
    data = ser.validated_data

    if User.objects.filter(email=data["email"]).exists():
        return Response(
            {"error": "email_already_exists"}, status=status.HTTP_409_CONFLICT
        )

    user = User.objects.create_user(
        email=data["email"],
        password=data["password"],
        name=data.get("name") or None,
        phone=data.get("phone") or None,
    )

    token = _token_for_user(user)
    return Response({"token": token, "user": UserSerializer(user).data})


@api_view(["POST"])
def login(request):
    email = (request.data or {}).get("email")
    password = (request.data or {}).get("password")
    if not email or not password:
        return Response(
            {"error": "email_and_password_required"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        user = User.objects.get(email=email)
    except User.DoesNotExist:
        return Response(
            {"error": "invalid_credentials"}, status=status.HTTP_401_UNAUTHORIZED
        )

    if not user.check_password(password):
        return Response(
            {"error": "invalid_credentials"}, status=status.HTTP_401_UNAUTHORIZED
        )

    token = _token_for_user(user)
    return Response({"token": token, "user": UserSerializer(user).data})
