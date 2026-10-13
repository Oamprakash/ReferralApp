package com.referral.app.resume

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Text from a resume PDF, read on the phone. Scanned (image-only) PDFs come back blank. */
@Singleton
class ResumeTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var initialised = false

    suspend fun extract(pdf: ByteArray): String = withContext(Dispatchers.Default) {
        if (!initialised) {
            PDFBoxResourceLoader.init(context)
            initialised = true
        }
        PDDocument.load(pdf).use { doc ->
            PDFTextStripper().apply {
                sortByPosition = true      // keeps two-column resumes readable line by line
                startPage = 1
                endPage = minOf(doc.numberOfPages, MAX_PAGES)
            }.getText(doc)
        }
    }

    private companion object { const val MAX_PAGES = 5 }
}
