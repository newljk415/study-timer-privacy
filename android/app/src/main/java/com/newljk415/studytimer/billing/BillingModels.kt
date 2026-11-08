package com.newljk415.studytimer.billing

import kotlinx.coroutines.flow.Flow

const val REMOVE_ADS_PRODUCT_ID = "remove_ads"
const val REMOVE_ADS_PURCHASE_OPTION_ID = "remove-ads-once"

enum class PurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/** Play Purchase 객체에서 필요한 값만 옮긴 것(테스트 가능하도록 Android 의존성 제거). */
data class PurchaseRecord(
    val products: List<String>,
    val state: PurchaseState,
    val purchaseToken: String,
    val isAcknowledged: Boolean,
    val packageName: String,
    val originalJson: String,
    val signature: String,
)

data class OfferInfo(
    val purchaseOptionId: String?,
    val offerId: String?,
    val offerToken: String,
    val formattedPrice: String,
)

sealed interface BillingResultOf<out T> {
    data class Ok<T>(val value: T) : BillingResultOf<T>
    /** [transient]: 네트워크·서비스 연결 등 일시적 오류. 어떤 오류든 소유권 상실로 취급하지 않는다. */
    data class Failure(val code: Int, val message: String, val transient: Boolean) : BillingResultOf<Nothing>
}

sealed interface PurchaseUpdate {
    data class Purchases(val purchases: List<PurchaseRecord>) : PurchaseUpdate
    data object Canceled : PurchaseUpdate
    data object AlreadyOwned : PurchaseUpdate
    data class Error(val code: Int, val message: String) : PurchaseUpdate
}

/** Play Billing 추상화. 실제 구현은 [PlayBillingGateway]. */
interface BillingGateway {
    val updates: Flow<PurchaseUpdate>
    suspend fun queryPurchases(): BillingResultOf<List<PurchaseRecord>>
    suspend fun queryOffers(): BillingResultOf<List<OfferInfo>>
    suspend fun acknowledge(purchaseToken: String): BillingResultOf<Unit>
}

/** 영속 캐시(DataStore). 오프라인·일시 오류 시 마지막으로 확인된 권한을 사용한다. */
interface EntitlementCache {
    suspend fun get(): Boolean
    suspend fun set(value: Boolean)
}

/** 구매 옵션 remove-ads-once 중 기본가(offerId 없음)를 우선 선택, 없으면 첫 할인 오퍼. */
fun selectRemoveAdsOffer(offers: List<OfferInfo>): OfferInfo? {
    val matching = offers.filter { it.purchaseOptionId == REMOVE_ADS_PURCHASE_OPTION_ID }
    return matching.firstOrNull { it.offerId == null } ?: matching.firstOrNull()
}
