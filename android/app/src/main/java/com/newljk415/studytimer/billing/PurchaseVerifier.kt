package com.newljk415.studytimer.billing

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

enum class Verification { VALID, INVALID, KEY_NOT_CONFIGURED }

/**
 * 서버 없는 클라이언트 검증.
 * - Play Console의 앱 라이선스 공개 키(Base64 RSA, 비밀 아님)로 originalJson 서명(SHA1withRSA)을 검증한다.
 * - 상품 ID, 패키지명, 구매 상태, 토큰 일치를 확인한다.
 * 한계: 기기·앱이 변조되면 클라이언트 검증은 우회될 수 있다. 서버 검증(Play Developer API)은
 * 서비스 계정 키가 필요하므로 앱에 넣지 않으며, 이 앱은 개발자 서버를 사용하지 않는다.
 */
class PurchaseVerifier(base64PublicKey: String?, private val expectedPackage: String) {
    private val key: PublicKey? = base64PublicKey?.takeIf { it.isNotBlank() }?.let {
        runCatching {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(it.trim())))
        }.getOrNull()
    }

    val configured: Boolean get() = key != null

    fun verify(p: PurchaseRecord): Verification {
        val k = key ?: return Verification.KEY_NOT_CONFIGURED
        if (REMOVE_ADS_PRODUCT_ID !in p.products) return Verification.INVALID
        if (p.packageName != expectedPackage) return Verification.INVALID
        if (p.state != PurchaseState.PURCHASED) return Verification.INVALID
        if (p.purchaseToken.isBlank() || !p.originalJson.contains("\"${p.purchaseToken}\"")) return Verification.INVALID
        if (p.signature.isBlank()) return Verification.INVALID
        return try {
            val sig = Signature.getInstance("SHA1withRSA")
            sig.initVerify(k)
            sig.update(p.originalJson.toByteArray(Charsets.UTF_8))
            if (sig.verify(Base64.getDecoder().decode(p.signature))) Verification.VALID else Verification.INVALID
        } catch (_: Exception) {
            Verification.INVALID
        }
    }
}
