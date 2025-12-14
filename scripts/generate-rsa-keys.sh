#!/bin/bash

# RSA 키페어 생성 스크립트
# JWT 토큰 서명을 위한 RSA 공개키/개인키 생성

set -e

CERTS_DIR="src/main/resources/certs"

# certs 디렉토리 생성
mkdir -p "$CERTS_DIR"

echo "🔐 RSA 키페어 생성 중..."

# 개인키 생성 (PKCS#8 형식)
openssl genrsa -out "$CERTS_DIR/private.pem" 2048

# 공개키 생성
openssl rsa -in "$CERTS_DIR/private.pem" -pubout -out "$CERTS_DIR/public.pem"

echo "✅ RSA 키페어 생성 완료!"
echo "   - 개인키: $CERTS_DIR/private.pem"
echo "   - 공개키: $CERTS_DIR/public.pem"
echo ""
echo "⚠️  주의: private.pem 파일은 절대 공개하지 마세요!"
echo "   .gitignore에 certs/ 디렉토리가 추가되었는지 확인하세요."
