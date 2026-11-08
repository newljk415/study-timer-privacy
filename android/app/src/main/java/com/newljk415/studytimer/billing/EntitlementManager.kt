package com.newljk415.studytimer.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BillingUiState(
    val entitled: Boolean = false,
    val pending: Boolean = false,
    val offer: OfferInfo? = null,
    val message: String? = null,
    val busy: Boolean = false,
)

/**
 * 광고 제거 권한 정책
 * - PURCHASED + 검증 성공 → 권한 부여(캐시 true) 후 미승인이면 승인. 승인 실패는 다음 재조회 때 재시도.
 * - PENDING → 권한 부여·승인하지 않음.
 * - 구매 재조회가 성공했고 유효한 구매가 없으면(환불·취소·미구매) 권한 해제(캐시 false).
 * - 재조회 실패(오프라인 등)는 캐시를 그대로 유지한다. 구매 업데이트 콜백만으로는 해제하지 않는다.
 * - 검증 키 미설정 빌드는 권한을 부여하지 않으며 기존 캐시도 바꾸지 않는다.
 * 모든 처리는 Mutex로 직렬화되어 중복 콜백·재조회에도 승인과 캐시 갱신이 안전하다.
 */
class EntitlementManager(
    private val gateway: BillingGateway,
    private val verifier: PurchaseVerifier,
    private val cache: EntitlementCache,
) {
    private val mutex = Mutex()
    private val acknowledged = HashSet<String>()
    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    suspend fun loadCache() { val v = cache.get(); _state.update { it.copy(entitled = v) } }

    /** 앱 실행·복귀·재연결·구매 복원 버튼에서 호출. */
    suspend fun refresh(userInitiated: Boolean = false) = mutex.withLock {
        _state.update { it.copy(busy = true) }
        when (val r = gateway.queryPurchases()) {
            is BillingResultOf.Failure -> _state.update {
                it.copy(busy = false, entitled = cache.get(),
                    message = if (userInitiated) "구매 정보를 확인하지 못했습니다. 네트워크 연결 후 다시 시도하세요." else it.message)
            }
            is BillingResultOf.Ok -> {
                process(r.value, authoritative = true)
                if (userInitiated) _state.update {
                    it.copy(message = when {
                        it.entitled -> "광고 제거 구매가 확인되었습니다."
                        it.pending -> "결제가 대기 중입니다. 완료되면 광고가 제거됩니다."
                        !verifier.configured -> "이 빌드에는 구매 검증 키가 설정되지 않았습니다."
                        else -> "복원할 구매가 없습니다."
                    })
                }
            }
        }
        if (_state.value.offer == null) loadOffer()
        _state.update { it.copy(busy = false) }
    }

    private suspend fun loadOffer() {
        when (val r = gateway.queryOffers()) {
            is BillingResultOf.Ok -> _state.update { it.copy(offer = selectRemoveAdsOffer(r.value)) }
            is BillingResultOf.Failure -> Unit // 가격 미표시, 구매 버튼 비활성
        }
    }

    suspend fun onUpdate(update: PurchaseUpdate) = mutex.withLock {
        when (update) {
            is PurchaseUpdate.Purchases -> process(update.purchases, authoritative = false)
            PurchaseUpdate.Canceled -> _state.update { it.copy(message = "구매가 취소되었습니다.") }
            is PurchaseUpdate.Error -> _state.update { it.copy(message = "구매를 완료하지 못했습니다. (코드 ${update.code})") }
            PurchaseUpdate.AlreadyOwned -> Unit // 권한 판단은 호출 측이 이어서 실행하는 재조회 결과로 한다.
        }
    }

    private suspend fun process(purchases: List<PurchaseRecord>, authoritative: Boolean) {
        val mine = purchases.filter { REMOVE_ADS_PRODUCT_ID in it.products }
        val valid = mine.filter { it.state == PurchaseState.PURCHASED && verifier.verify(it) == Verification.VALID }
        val pending = mine.any { it.state == PurchaseState.PENDING }
        if (valid.isNotEmpty()) {
            cache.set(true)
            _state.update { it.copy(entitled = true, pending = false) }
            valid.filter { !it.isAcknowledged && it.purchaseToken !in acknowledged }.forEach { p ->
                if (gateway.acknowledge(p.purchaseToken) is BillingResultOf.Ok) acknowledged += p.purchaseToken
            }
            return
        }
        if (authoritative && verifier.configured) {
            cache.set(false)
            _state.update { it.copy(entitled = false, pending = pending) }
        } else {
            _state.update { it.copy(pending = if (authoritative) pending else pending || it.pending) }
        }
        if (mine.any { it.state == PurchaseState.PURCHASED } && valid.isEmpty() && verifier.configured) {
            _state.update { it.copy(message = "구매 검증에 실패했습니다.") }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
    fun postMessage(msg: String) = _state.update { it.copy(message = msg) }
}
