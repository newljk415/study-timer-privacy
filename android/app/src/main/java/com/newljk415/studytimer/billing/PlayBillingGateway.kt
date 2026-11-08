package com.newljk415.studytimer.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

class PlayBillingGateway(context: Context) : BillingGateway {
    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val updates: SharedFlow<PurchaseUpdate> = _updates

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            _updates.tryEmit(
                when (result.responseCode) {
                    BillingResponseCode.OK -> PurchaseUpdate.Purchases(purchases.orEmpty().map { it.toRecord() })
                    BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Canceled
                    BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseUpdate.AlreadyOwned
                    else -> PurchaseUpdate.Error(result.responseCode, result.debugMessage)
                },
            )
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection() // 연결이 끊긴 뒤 API 호출 시 자동 재연결
        .build()

    private val connectMutex = Mutex()
    @Volatile private var details: ProductDetails? = null

    private suspend fun connect(): BillingResult? = connectMutex.withLock {
        if (client.isReady) return null
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.takeIf { it.responseCode != BillingResponseCode.OK })
                }
                override fun onBillingServiceDisconnected() { /* 자동 재연결 사용 */ }
            })
        }
    }

    private fun BillingResult.failure() = BillingResultOf.Failure(
        responseCode, debugMessage,
        transient = responseCode in setOf(
            BillingResponseCode.SERVICE_DISCONNECTED, BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.NETWORK_ERROR, BillingResponseCode.ERROR, BillingResponseCode.BILLING_UNAVAILABLE,
        ),
    )

    override suspend fun queryPurchases(): BillingResultOf<List<PurchaseRecord>> {
        connect()?.let { return it.failure() }
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        return suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { result, purchases ->
                cont.resume(if (result.responseCode == BillingResponseCode.OK)
                    BillingResultOf.Ok(purchases.map { it.toRecord() }) else result.failure())
            }
        }
    }

    override suspend fun queryOffers(): BillingResultOf<List<OfferInfo>> {
        connect()?.let { return it.failure() }
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(REMOVE_ADS_PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build(),
        )).build()
        return suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, r ->
                if (result.responseCode != BillingResponseCode.OK) { cont.resume(result.failure()); return@queryProductDetailsAsync }
                val pd = r.productDetailsList.firstOrNull { it.productId == REMOVE_ADS_PRODUCT_ID }
                details = pd
                cont.resume(BillingResultOf.Ok(pd?.oneTimePurchaseOfferDetailsList.orEmpty().mapNotNull {
                    val token = it.offerToken ?: return@mapNotNull null
                    OfferInfo(it.purchaseOptionId, it.offerId, token, it.formattedPrice)
                }))
            }
        }
    }

    override suspend fun acknowledge(purchaseToken: String): BillingResultOf<Unit> {
        connect()?.let { return it.failure() }
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        return suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result ->
                cont.resume(if (result.responseCode == BillingResponseCode.OK) BillingResultOf.Ok(Unit) else result.failure())
            }
        }
    }

    /** 결제 화면 실행. 결과는 [updates]로 전달된다. */
    fun launch(activity: Activity, offer: OfferInfo): Int {
        val pd = details ?: return BillingResponseCode.ITEM_UNAVAILABLE
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).setOfferToken(offer.offerToken).build(),
        )).build()
        return client.launchBillingFlow(activity, params).responseCode
    }
}

private fun Purchase.toRecord() = PurchaseRecord(
    products = products,
    state = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
        Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
        else -> PurchaseState.UNSPECIFIED
    },
    purchaseToken = purchaseToken,
    isAcknowledged = isAcknowledged,
    packageName = packageName,
    originalJson = originalJson,
    signature = signature,
)
