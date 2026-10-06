package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.KnowledgeItem
import com.example.data.repository.KnowledgeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var knowledgeRepo: KnowledgeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        knowledgeRepo = KnowledgeRepository(database.knowledgeDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HR Advisor", appName)
    }

    @Test
    fun `HR knowledge repository seed and RAG case retrieval test`() = runBlocking {
        // Test seeding initial HR labor regulations & SOPs
        knowledgeRepo.seedInitialDataIfEmpty()

        val results = knowledgeRepo.findRelevantKnowledge("bagaimana prosedur penanganan karyawan mangkir 5 hari kerja?", limit = 2)
        assertTrue("Harus menemukan ketentuan terkait mangkir kerja", results.isNotEmpty())
        assertTrue("Judul harus memuat ketentuan mangkir", results.first().title.contains("Mangkir", ignoreCase = true))
    }

    @Test
    fun `insert custom company regulation and retrieve for HR case analysis`() = runBlocking {
        val item = KnowledgeItem(
            title = "SOP Investigasi Fraud & Etika Karyawan Internal",
            category = "Pelanggaran Berat & Fraud",
            content = "Setiap indikasi fraud internal wajib dilaporkan ke Komite Etik dan HR dalam kurun waktu 1x24 jam dengan melampirkan bukti rekaman dan dokumen transaksi.",
            keywords = "fraud, investigasi, komite etik, audit internal",
            sourceType = "MANUAL"
        )
        val id = knowledgeRepo.insert(item)
        assertTrue(id > 0)

        val found = knowledgeRepo.findRelevantKnowledge("kronologi dugaan fraud kasir cabang", limit = 1)
        assertTrue(found.isNotEmpty())
        assertEquals("SOP Investigasi Fraud & Etika Karyawan Internal", found.first().title)
    }
}
