package com.vurka.emogy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.vurka.emogy.data.GameKeys
import com.vurka.emogy.data.store.PurchaseResult
import com.vurka.emogy.data.store.StoreCatalog
import com.vurka.emogy.data.store.StoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: StoreRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "store.preferences_pb")
        }
        repository = StoreRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun seedCoins(amount: Int) = runBlocking {
        dataStore.edit { it[GameKeys.COINS] = amount }
    }

    private fun seedStars(levels: Int) = runBlocking {
        dataStore.edit { prefs ->
            (1..levels).forEach { level -> prefs[intPreferencesKey("stars_$level")] = 2 }
        }
    }

    private fun seedLegacyPurchase(themeId: String) = runBlocking {
        dataStore.edit { it[booleanPreferencesKey("theme_purchased_$themeId")] = true }
    }

    private fun coins(): Int = runBlocking { repository.coins.first() }
    private fun hints(): Int = runBlocking { repository.hintTokens.first() }
    private fun owned(): Set<String> = runBlocking { repository.owned.first() }

    @Test
    fun `buying a hint pack deducts coins and adds hints in one transaction`() = runBlocking {
        seedCoins(1_000)

        val result = repository.purchase("hints_5")

        assertTrue(result is PurchaseResult.Success)
        val success = result as PurchaseResult.Success
        assertEquals(5, success.item.hints)
        assertEquals(750, success.remainingCoins)
        assertEquals(750, coins())
        assertEquals(5, hints())
    }

    @Test
    fun `an unaffordable purchase changes nothing`() = runBlocking {
        seedCoins(10)

        val result = repository.purchase("hints_5")

        assertEquals(PurchaseResult.InsufficientCoins, result)
        assertEquals(10, coins())
        assertEquals(0, hints())
    }

    @Test
    fun `a cosmetic is charged at most once`() = runBlocking {
        seedCoins(20_000)

        val first = repository.purchase("red_crimson")
        val second = repository.purchase("red_crimson")

        assertTrue(first is PurchaseResult.Success)
        assertEquals(PurchaseResult.AlreadyOwned, second)
        assertEquals(20_000 - 1_500, coins())
        assertTrue("red_crimson" in owned())
    }

    @Test
    fun `two concurrent taps on the same cosmetic produce one charge`() = runBlocking {
        seedCoins(20_000)

        val results = kotlinx.coroutines.coroutineScope {
            val first = async(Dispatchers.IO) { repository.purchase("royal_gold") }
            val second = async(Dispatchers.IO) { repository.purchase("royal_gold") }
            listOf(first.await(), second.await())
        }

        assertEquals(1, results.count { it is PurchaseResult.Success })
        assertEquals(1, results.count { it == PurchaseResult.AlreadyOwned })
        assertEquals(20_000 - 4_000, coins())
    }

    @Test
    fun `a milestone unlocked cosmetic is free and cannot be bought again`() = runBlocking {
        seedCoins(20_000)
        seedStars(50)

        val result = repository.purchase("red_crimson")

        assertEquals(PurchaseResult.AlreadyOwned, result)
        assertEquals(20_000, coins())
    }

    @Test
    fun `a few levels short of the milestone the cosmetic is still purchasable`() = runBlocking {
        seedCoins(20_000)
        seedStars(49)

        val result = repository.purchase("red_crimson")

        assertTrue(result is PurchaseResult.Success)
        assertEquals(20_000 - 1_500, coins())
    }

    @Test
    fun `a legacy purchase flag counts as ownership`() = runBlocking {
        seedCoins(20_000)
        seedLegacyPurchase("aurora_mint")

        val result = repository.purchase("aurora_mint")

        assertEquals(PurchaseResult.AlreadyOwned, result)
        assertEquals(20_000, coins())
        assertTrue("aurora_mint" in owned())
    }

    @Test
    fun `a bundle grants every hint it advertises`() = runBlocking {
        seedCoins(1_500)

        val result = repository.purchase("bundle_40_hints")

        assertTrue(result is PurchaseResult.Success)
        assertEquals(40, hints())
        assertEquals(0, coins())
    }

    @Test
    fun `unknown items are rejected without touching the balance`() = runBlocking {
        seedCoins(500)

        val result = repository.purchase("does_not_exist")

        assertEquals(PurchaseResult.UnknownItem, result)
        assertEquals(500, coins())
    }

    @Test
    fun `every catalogue item has a unique id and a positive price`() {
        val ids = StoreCatalog.items.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(StoreCatalog.items.all { it.price > 0 })
        assertTrue(StoreCatalog.items.all { StoreCatalog.item(it.id) == it })
    }

    @Test
    fun `reward only cosmetics are never sold`() {
        val rewardOnly = StoreCatalog.cosmetics.filter { it.rewardOnly }
        assertTrue(rewardOnly.isNotEmpty())
        rewardOnly.forEach { cosmetic ->
            assertFalse(cosmetic.purchasable)
            assertEquals(null, StoreCatalog.item(cosmetic.id))
        }
    }

    @Test
    fun `mystery box grants hints and bonus coins without touching streak preferences`() = runBlocking {
        seedCoins(3_000)

        val result = repository.purchase("bundle_mystery_box")

        assertTrue(result is PurchaseResult.Success)
        val success = result as PurchaseResult.Success
        assertEquals(15, success.mysteryBox?.hints)
        assertEquals(750, success.mysteryBox?.bonusCoins)
        assertEquals(15, hints())
        assertEquals(1_750, success.remainingCoins)
    }
}
