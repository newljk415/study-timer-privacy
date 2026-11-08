package com.newljk415.studytimer.billing

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

private const val PKG = "com.newljk415.studytimer"

class FakeGateway : BillingGateway {
    override val updates = MutableSharedFlow<PurchaseUpdate>()
    var purchases: BillingResultOf<List<PurchaseRecord>> = BillingResultOf.Ok(emptyList())
    var offers: BillingResultOf<List<OfferInfo>> = BillingResultOf.Ok(emptyList())
    var ackResult: BillingResultOf<Unit> = BillingResultOf.Ok(Unit)
    val ackCalls = mutableListOf<String>()
    override suspend fun queryPurchases() = purchases
    override suspend fun queryOffers() = offers
    override suspend fun acknowledge(purchaseToken: String): BillingResultOf<Unit> { ackCalls += purchaseToken; return ackResult }
}

class FakeCache(var value: Boolean = false) : EntitlementCache {
    var writes = 0
    override suspend fun get() = value
    override suspend fun set(value: Boolean) { this.value = value; writes++ }
}

class EntitlementManagerTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
    private val gateway = FakeGateway()
    private val cache = FakeCache()
    private val verifier = PurchaseVerifier(publicKey, PKG)
    private val manager = EntitlementManager(gateway, verifier, cache)

    private fun sign(json: String, key: PrivateKey = keys.private): String =
        Base64.getEncoder().encodeToString(Signature.getInstance("SHA1withRSA").run {
            initSign(key); update(json.toByteArray()); sign()
        })

    private fun purchase(
        state: PurchaseState = PurchaseState.PURCHASED, token: String = "tok-1", acked: Boolean = false,
        product: String = REMOVE_ADS_PRODUCT_ID, pkg: String = PKG, signer: PrivateKey = keys.private,
    ): PurchaseRecord {
        val stateNum = if (state == PurchaseState.PURCHASED) 0 else 4
        val json = """{"packageName":"$pkg","productId":"$product","purchaseState":$stateNum,"purchaseToken":"$token","acknowledged":$acked}"""
        return PurchaseRecord(listOf(product), state, token, acked, pkg, json, sign(json, signer))
    }

    @Test fun pendingDoesNotGrantOrAcknowledge() = runTest {
        gateway.purchases = BillingResultOf.Ok(listOf(purchase(PurchaseState.PENDING)))
        manager.refresh()
        assertFalse(manager.state.value.entitled)
        assertTrue(manager.state.value.pending)
        assertFalse(cache.value)
        assertTrue(gateway.ackCalls.isEmpty())
        manager.onUpdate(PurchaseUpdate.Purchases(listOf(purchase(PurchaseState.PENDING))))
        assertFalse(cache.value)
        assertTrue(gateway.ackCalls.isEmpty())
    }

    @Test fun purchasedIsVerifiedGrantedAndAcknowledgedOnce() = runTest {
        val p = purchase()
        manager.onUpdate(PurchaseUpdate.Purchases(listOf(p)))
        assertTrue(cache.value)
        assertTrue(manager.state.value.entitled)
        assertEquals(listOf("tok-1"), gateway.ackCalls)
        // 중복 콜백·재조회(아직 미승인으로 보고되는 경우 포함)에도 승인은 한 번
        manager.onUpdate(PurchaseUpdate.Purchases(listOf(p)))
        gateway.purchases = BillingResultOf.Ok(listOf(p))
        manager.refresh()
        val concurrent = List(5) { async { manager.refresh() } }
        concurrent.awaitAll()
        assertEquals(listOf("tok-1"), gateway.ackCalls)
        assertTrue(cache.value)
    }

    @Test fun pendingThenPurchasedGrantsAfterCompletion() = runTest {
        gateway.purchases = BillingResultOf.Ok(listOf(purchase(PurchaseState.PENDING)))
        manager.refresh(); assertFalse(cache.value)
        gateway.purchases = BillingResultOf.Ok(listOf(purchase(PurchaseState.PURCHASED)))
        manager.refresh()
        assertTrue(cache.value)
        assertFalse(manager.state.value.pending)
    }

    @Test fun alreadyAcknowledgedPurchaseIsNotReAcknowledged() = runTest {
        gateway.purchases = BillingResultOf.Ok(listOf(purchase(acked = true)))
        manager.refresh()
        assertTrue(cache.value)
        assertTrue(gateway.ackCalls.isEmpty())
    }

    @Test fun failedAcknowledgeIsRetriedOnNextRefresh() = runTest {
        gateway.ackResult = BillingResultOf.Failure(12, "network", transient = true)
        gateway.purchases = BillingResultOf.Ok(listOf(purchase()))
        manager.refresh()
        assertTrue(cache.value) // 검증된 구매는 권한 부여
        gateway.ackResult = BillingResultOf.Ok(Unit)
        manager.refresh()
        manager.refresh()
        assertEquals(listOf("tok-1", "tok-1"), gateway.ackCalls)
    }

    @Test fun invalidSignatureOrMismatchedDataIsRejected() = runTest {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val forged = purchase(signer = other.private)
        val wrongPkg = purchase(pkg = "evil.app")
        val tampered = purchase().let { it.copy(originalJson = it.originalJson.replace("tok-1", "tok-2")) }
        listOf(forged, wrongPkg, tampered).forEach { assertEquals(Verification.INVALID, verifier.verify(it)) }
        gateway.purchases = BillingResultOf.Ok(listOf(forged, wrongPkg, tampered))
        manager.refresh()
        assertFalse(cache.value)
        assertTrue(gateway.ackCalls.isEmpty())
    }

    @Test fun missingKeyNeverGrants() = runTest {
        val m = EntitlementManager(gateway, PurchaseVerifier(null, PKG), cache)
        gateway.purchases = BillingResultOf.Ok(listOf(purchase()))
        m.refresh()
        assertFalse(cache.value)
        assertTrue(gateway.ackCalls.isEmpty())
        assertEquals(Verification.KEY_NOT_CONFIGURED, PurchaseVerifier("", PKG).verify(purchase()))
    }

    @Test fun queryFailureKeepsCachedEntitlement() = runTest {
        cache.value = true
        gateway.purchases = BillingResultOf.Failure(-1, "disconnected", transient = true)
        manager.refresh(userInitiated = true)
        assertTrue(cache.value)
        assertTrue(manager.state.value.entitled)
        assertTrue(manager.state.value.message!!.contains("확인하지 못했습니다"))
        assertEquals(0, cache.writes)
    }

    @Test fun successfulQueryWithoutPurchaseRevokesRefundedEntitlement() = runTest {
        cache.value = true
        gateway.purchases = BillingResultOf.Ok(emptyList()) // 환불·취소로 구매 목록에서 사라짐
        manager.refresh()
        assertFalse(cache.value)
        assertFalse(manager.state.value.entitled)
    }

    @Test fun purchaseCallbackWithoutItemDoesNotRevoke() = runTest {
        cache.value = true
        manager.loadCache()
        manager.onUpdate(PurchaseUpdate.Purchases(emptyList()))
        assertTrue(cache.value)
    }

    @Test fun canceledAndErrorUpdatesOnlyShowMessages() = runTest {
        manager.onUpdate(PurchaseUpdate.Canceled)
        assertEquals("구매가 취소되었습니다.", manager.state.value.message)
        manager.onUpdate(PurchaseUpdate.Error(6, "x"))
        assertTrue(manager.state.value.message!!.contains("6"))
        assertFalse(cache.value)
    }

    @Test fun restoreFindsExistingPurchase() = runTest {
        gateway.purchases = BillingResultOf.Ok(listOf(purchase(acked = true)))
        manager.refresh(userInitiated = true)
        assertTrue(cache.value)
        assertEquals("광고 제거 구매가 확인되었습니다.", manager.state.value.message)
    }

    @Test fun offerSelectionPrefersBasePriceOfPurchaseOption() = runTest {
        val offers = listOf(
            OfferInfo("other-option", null, "t0", "₩500"),
            OfferInfo(REMOVE_ADS_PURCHASE_OPTION_ID, "launch-discount", "t1", "₩1,000"),
            OfferInfo(REMOVE_ADS_PURCHASE_OPTION_ID, null, "t2", "₩2,200"),
        )
        assertEquals("t2", selectRemoveAdsOffer(offers)?.offerToken)
        assertEquals("t1", selectRemoveAdsOffer(offers.take(2))?.offerToken)
        assertEquals(null, selectRemoveAdsOffer(offers.take(1)))
        gateway.offers = BillingResultOf.Ok(offers)
        manager.refresh()
        assertEquals("₩2,200", manager.state.value.offer?.formattedPrice)
    }
}
