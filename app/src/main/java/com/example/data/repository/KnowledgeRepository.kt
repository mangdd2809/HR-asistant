package com.example.data.repository

import com.example.data.local.KnowledgeDao
import com.example.data.local.KnowledgeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class KnowledgeRepository(private val knowledgeDao: KnowledgeDao) {

    val allKnowledge: Flow<List<KnowledgeItem>> = knowledgeDao.getAllKnowledgeFlow()
    val activeKnowledge: Flow<List<KnowledgeItem>> = knowledgeDao.getActiveKnowledgeFlow()

    fun search(query: String): Flow<List<KnowledgeItem>> {
        return if (query.isBlank()) {
            knowledgeDao.getAllKnowledgeFlow()
        } else {
            knowledgeDao.searchKnowledge(query.trim())
        }
    }

    suspend fun insert(item: KnowledgeItem): Long = withContext(Dispatchers.IO) {
        knowledgeDao.insert(item)
    }

    suspend fun update(item: KnowledgeItem) = withContext(Dispatchers.IO) {
        knowledgeDao.update(item)
    }

    suspend fun delete(item: KnowledgeItem) = withContext(Dispatchers.IO) {
        knowledgeDao.delete(item)
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        knowledgeDao.deleteById(id)
    }

    suspend fun toggleActive(item: KnowledgeItem) = withContext(Dispatchers.IO) {
        val updated = item.copy(isActive = !item.isActive, updatedAt = System.currentTimeMillis())
        knowledgeDao.update(updated)
    }

    /**
     * RAG Knowledge Retriever for HR Case Analysis:
     * Finds active company regulations, PP, PKB, and labor laws matching case scenario tokens.
     */
    suspend fun findRelevantKnowledge(userQuery: String, limit: Int = 3): List<KnowledgeItem> =
        withContext(Dispatchers.IO) {
            val activeItems = knowledgeDao.getActiveKnowledge()
            if (activeItems.isEmpty()) return@withContext emptyList()

            val normalizedQuery = userQuery.lowercase().trim()
            val queryTokens = normalizedQuery
                .split(" ", ",", ".", "?", "!", "-", "/", "\n")
                .filter { it.length > 2 }
                .toSet()

            val scoredItems = activeItems.map { item ->
                var score = 0
                val titleLower = item.title.lowercase()
                val contentLower = item.content.lowercase()
                val keywordsLower = item.keywords.lowercase()
                val categoryLower = item.category.lowercase()

                // Exact phrase matches
                if (normalizedQuery.isNotEmpty() && titleLower.contains(normalizedQuery)) score += 35
                if (normalizedQuery.isNotEmpty() && keywordsLower.contains(normalizedQuery)) score += 25
                if (normalizedQuery.isNotEmpty() && contentLower.contains(normalizedQuery)) score += 15

                // Token matches
                for (token in queryTokens) {
                    if (titleLower.contains(token)) score += 10
                    if (keywordsLower.contains(token)) score += 8
                    if (categoryLower.contains(token)) score += 6
                    if (contentLower.contains(token)) score += 3
                }

                Pair(item, score)
            }

            scoredItems
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }

    /**
     * Seed initial Indonesian labor regulations, standard company rules (PP/PKB),
     * and HR SOPs as baseline knowledge for analyzing employee cases.
     */
    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        if (knowledgeDao.getCount() == 0) {
            val defaultItems = listOf(
                KnowledgeItem(
                    title = "Ketentuan Mangkir Kerja & Prosedur PHK Otomatis (PP 35/2021)",
                    category = "Mangkir & Resign",
                    content = """
                        Dasar Regulasi: Pasal 52 PP No. 35 Tahun 2021.
                        Ketentuan:
                        1. Karyawan yang mangkir (tidak masuk kerja tanpa keterangan tertulis sah) selama 5 (lima) hari kerja berturut-turut dapat diputus hubungan kerjanya dengan kualifikasi mengundurkan diri.
                        2. Prosedur Wajib HR:
                           - HR wajib melayangkan Surat Panggilan Resmi ke-1 ke alamat terdaftar karyawan.
                           - Jika tidak hadir dalam tenggat waktu 3 hari kerja, layangkan Surat Panggilan Resmi ke-2.
                           - Pemanggilan harus dilakukan secara patut dan tertulis (dibuktikan tanda terima kurir/pos/surat tercatat).
                        3. Konsekuensi Hukum: Jika setelah 2 kali pemanggilan karyawan tetap tidak hadir atau tidak memberikan alasan sah, HR dapat menerbitkan SK PHK kualifikasi mengundurkan diri.
                        4. Hak Karyawan: Berhak atas Uang Penggantian Hak (UPH) dan Uang Pisah sesuai ketentuan Peraturan Perusahaan/PKB, namun TIDAK berhak atas Uang Pesangon (UP) dan UPMK.
                    """.trimIndent(),
                    keywords = "mangkir, tidak masuk, absen, mengundurkan diri, panggilan 1, panggilan 2, tanpa keterangan, phk mangkir, uang pisah",
                    sourceType = "SYSTEM_DEFAULT"
                ),
                KnowledgeItem(
                    title = "SOP Pemberian Surat Peringatan (SP 1, SP 2, SP 3) & Masa Berlaku",
                    category = "Disiplin & Sanksi",
                    content = """
                        Dasar Regulasi: Pasal 52 ayat (1) PP No. 35 Tahun 2021 & Peraturan Perusahaan.
                        Tingkatan Sanksi Disiplin:
                        1. SP-1 (Peringatan Pertama): Diberikan atas pelanggaran ringan/sedang (misal keterlambatan berulang >3 kali sebulan, tidak memakai seragam/APD, mengabaikan instruksi atasan).
                        2. SP-2 (Peringatan Kedua): Diberikan jika melakukan pelanggaran serupa atau jenis lain dalam kurun waktu masa aktif SP-1.
                        3. SP-3 (Peringatan Ketiga/Terakhir): Diberikan jika masih melanggar dalam kurun waktu masa aktif SP-2. Menjadi dasar pertimbangan PHK atas dasar pelanggaran disiplin.
                        Masa Berlaku: Setiap Surat Peringatan berlaku selama 6 (enam) bulan sejak tanggal diterbitkan, kecuali diatur lain dalam PKB secara lebih spesifik.
                        Prosedur Wajib HR:
                        - Lakukan klarifikasi/investigasi internal terlebih dahulu dan tuangkan dalam Berita Acara Pemeriksaan (BAP).
                        - Berikan kesempatan pembelaan diri bagi karyawan.
                        - Terbitkan Surat Peringatan resmi yang ditandatangani oleh atasan dan HR Manager serta salinan bertanda tangan karyawan sebagai bukti penerimaan.
                    """.trimIndent(),
                    keywords = "surat peringatan, sp1, sp2, sp3, sanksi, disiplin, bap, 6 bulan, keterlambatan, indisipliner, teguran tertulis",
                    sourceType = "SYSTEM_DEFAULT"
                ),
                KnowledgeItem(
                    title = "Penanganan Pelanggaran Berat & Tindak Pidana Karyawan",
                    category = "Pelanggaran Berat & Fraud",
                    content = """
                        Dasar Regulasi: Pasal 52 ayat (2) PP No. 35 Tahun 2021 jo Putusan MK No. 012/PUU-I/2003.
                        Kategori Pelanggaran Berat:
                        1. Penipuan, pencurian, atau penggelapan uang/barang milik perusahaan atau rekan kerja.
                        2. Memberikan keterangan palsu atau yang dipalsukan saat proses rekrutmen atau tugas.
                        3. Mengonsumsi alkohol, mengedarkan/menggunakan narkoba di lingkungan kerja.
                        4. Melakukan perbuatan asusila atau perjudian di lingkungan kerja.
                        5. Menyerang, menganiaya, mengancam, atau mengintimidasi teman sekerja atau pimpinan.
                        6. Membongkar rahasia perusahaan kepada pihak pesaing.
                        Langkah Penanganan HR:
                        - Kumpulkan bukti konkret (CCTV, rekaman transaksi audit, dokumen palsu, kesaksian tertulis).
                        - HR dapat menjatuhkan Skorsing Sementara selama proses investigasi atau pelaporan kepolisian berjalan, dengan catatan perusahaan TETAP WAJIB membayar upah pokok dan tunjangan tetap karyawan.
                        - PHK dapat diproses berdasarkan bukti pelanggaran mendesak yang telah diatur eksplisit dalam Peraturan Perusahaan / PKB.
                    """.trimIndent(),
                    keywords = "pelanggaran berat, pencurian, fraud, penggelapan uang, cctv, skorsing, pidana, polisi, asusila, narkoba, rahasia perusahaan",
                    sourceType = "SYSTEM_DEFAULT"
                ),
                KnowledgeItem(
                    title = "Formula Kompensasi PHK: Pesangon, UPMK, & UPH (PP 35/2021)",
                    category = "PHK & Pesangon",
                    content = """
                        Dasar Regulasi: Pasal 40 s/d 59 PP No. 35 Tahun 2021.
                        Komponen Hak PHK:
                        1. Uang Pesangon (UP):
                           - Masa kerja < 1 tahun: 1 bulan upah.
                           - 1 - < 2 tahun: 2 bulan upah (bertambah 1 bulan tiap tahun hingga maksimal 9 bulan untuk masa kerja >= 8 tahun).
                        2. Uang Penghargaan Masa Kerja (UPMK):
                           - Mulai berlaku masa kerja 3 tahun (3 - < 6 tahun: 2 bulan upah, 6 - < 9 tahun: 3 bulan upah, s/d maksimal 10 bulan upah untuk >= 24 tahun).
                        3. Uang Penggantian Hak (UPH):
                           - Cuti tahunan yang belum gugur dan belum diambil.
                           - Biaya atau ongkos pulang bagi karyawan dan keluarganya ke tempat asal rekrutmen.
                        Faktor Pengali berdasarkan Alasan PHK:
                        - Efisiensi rugi: 0.5x UP + 1x UPMK + 1x UPH.
                        - Pelanggaran disiplin (SP3): 0.5x UP + 1x UPMK + 1x UPH.
                        - Pensiun normal: 1.75x UP + 1x UPMK + 1x UPH.
                        - Meninggal dunia: 2x UP + 1x UPMK + 1x UPH.
                    """.trimIndent(),
                    keywords = "pesangon, upmk, uph, kompensasi phk, uang penghargaan, formula pesangon, efisiensi, perhitungan phk, pensiun",
                    sourceType = "SYSTEM_DEFAULT"
                ),
                KnowledgeItem(
                    title = "Hak Cuti Sakit Berkepanjangan & Batas PHK Kesehatan",
                    category = "Cuti, Sakit & Izin",
                    content = """
                        Dasar Regulasi: Pasal 93 ayat (3) UU Ketenagakerjaan jo Pasal 153 ayat (1) huruf a UU 13/2003 & PP 35/2021.
                        Aturan Upah Sakit Berkepanjangan:
                        1. Selama karyawan sakit berdasarkan surat keterangan dokter resmi, perusahaan dilarang mem-PHK dalam waktu 12 bulan pertama.
                        2. Skema Pembayaran Upah Sakit Berkelanjutan:
                           - 4 (empat) bulan pertama: dibayar 100% upah.
                           - 4 (empat) bulan kedua: dibayar 75% upah.
                           - 4 (empat) bulan ketiga: dibayar 50% upah.
                           - Bulan selanjutnya: dibayar 25% upah sebelum pemutusan hubungan kerja dilakukan.
                        3. Setelah melampaui 12 bulan berturut-turut dan belum mampu bekerja: Perusahaan dapat mengajukan PHK karena alasan sakit berkepanjangan dengan hak: 2x Uang Pesangon + 1x UPMK + UPH (Pasal 45 PP 35/2021).
                    """.trimIndent(),
                    keywords = "sakit berkepanjangan, surat dokter, opname, rawat jalan, upah sakit, 12 bulan, phk kesehatan, sakit menahun",
                    sourceType = "SYSTEM_DEFAULT"
                ),
                KnowledgeItem(
                    title = "Kebijakan Mutasi, Demosi, & Penolakan Penugasan Kerja",
                    category = "Mutasi & Kinerja",
                    content = """
                        Dasar Regulasi: Pasal 32 UU Ketenagakerjaan & Doktrin Hubungan Kerja (Perjanjian Kerja).
                        Prinsip Hukum Mutasi & Demosi:
                        1. Mutasi adalah hak prerogatif manajemen untuk kebutuhan operasional bisnis sepanjang klausul tersebut tercantum dalam Perjanjian Kerja / PP / PKB ("Bersedia ditempatkan di seluruh unit usaha").
                        2. Syarat Keabsahan Mutasi:
                           - Diberikan surat keputusan (SK) tertulis resmi dengan waktu persiapan wajar (minimal 14-30 hari).
                           - Tidak boleh menurunkan upah pokok atau tunjangan tetap yang melekat pada individu tanpa persetujuan tertulis.
                           - Disediakan tunjangan relokasi/akomodasi jika mutasi lintas kota/provinsi.
                        3. Sikap atas Penolakan Mutasi:
                           - Jika penolakan tanpa alasan sah dan klausul mutasi sudah disepakati di awal kontrak, hal ini dikualifikasikan sebagai pembangkangan instruksi kerja (insubordination).
                           - HR dapat memproses penegakan disiplin mulai dari SP-1, SP-2, hingga SP-3.
                    """.trimIndent(),
                    keywords = "mutasi, demosi, rotasi, penolakan mutasi, pindah cabang, sk mutasi, penurunan jabatan, insubordinasi, membangkang",
                    sourceType = "SYSTEM_DEFAULT"
                ),
                KnowledgeItem(
                    title = "Aturan Batas Waktu Lembur & Perhitungan Upah Lembur (Overtime)",
                    category = "Upah, Lembur & Tunjangan",
                    content = """
                        Dasar Regulasi: PP No. 35 Tahun 2021 Pasal 26 - 31.
                        Ketentuan Jam Kerja & Lembur:
                        1. Jam Kerja Standar: 7 jam/hari (40 jam/minggu untuk 6 hari kerja) atau 8 jam/hari (40 jam/minggu untuk 5 hari kerja).
                        2. Batas Maksimal Lembur: Paling banyak 4 jam dalam 1 hari dan 18 jam dalam 1 minggu (tidak termasuk lembur pada hari istirahat mingguan/libur resmi).
                        3. Syarat Wajib: Harus ada Surat Perintah Kerja Lembur (SPKL) tertulis dan persetujuan pekerja, serta perusahaan wajib menyediakan makanan/minuman minimal 1.400 kkal jika lembur >= 4 jam.
                        4. Rumus Upah Lembur per Jam: 1/173 x Upah Sebulan (Upah Pokok + Tunjangan Tetap).
                           - Hari Kerja Biasa: Jam pertama dibayar 1.5x upah per jam, jam kedua dan seterusnya dibayar 2x upah per jam.
                           - Hari Libur Resmi: 5 jam pertama 2x upah per jam, jam ke-6 3x, jam ke-7 & 8 4x upah per jam.
                    """.trimIndent(),
                    keywords = "lembur, spkl, overtime, upah lembur, batas jam lembur, perhitungan 1/173, makanan lembur, jam kerja",
                    sourceType = "SYSTEM_DEFAULT"
                )
            )
            knowledgeDao.insertAll(defaultItems)
        }
    }
}
