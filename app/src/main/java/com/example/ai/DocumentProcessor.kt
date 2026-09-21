package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.example.model.DocumentOcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object DocumentProcessor {

    suspend fun processImageUri(context: Context, uri: Uri): Pair<Bitmap, String> = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open image input stream")
        val bitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw IllegalArgumentException("Failed to decode bitmap from image")
        
        val base64 = bitmapToBase64(bitmap)
        Pair(bitmap, base64)
    }

    suspend fun processPdfUri(context: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
        val pageBitmaps = mutableListOf<Bitmap>()
        val fileDescriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return@withContext pageBitmaps

        fileDescriptor.use { pfd ->
            val pdfRenderer = PdfRenderer(pfd)
            val maxPages = minOf(pdfRenderer.pageCount, 5) // Process up to first 5 pages for AI OCR
            for (i in 0 until maxPages) {
                val page = pdfRenderer.openPage(i)
                // Render at high resolution for good OCR
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pageBitmaps.add(bitmap)
                page.close()
            }
            pdfRenderer.close()
        }
        pageBitmaps
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Resize bitmap if too large to save bandwidth
        val maxDim = 1280
        val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun getSampleDocumentText(sampleKey: String): DocumentOcrResult {
        return when (sampleKey) {
            "BIO" -> DocumentOcrResult(
                fileName = "Cellular_Respiration_Notes.pdf",
                sourceType = "PDF",
                extractedText = """
                    CHAPTER 4: CELLULAR RESPIRATION & ENERGY METABOLISM
                    
                    Cellular respiration is the process by which cells break down glucose and oxygen to produce ATP (adenosine triphosphate), water, and carbon dioxide. The overall equation is:
                    C6H12O6 + 6 O2 -> 6 CO2 + 6 H2O + 36-38 ATP
                    
                    The process consists of three main stages:
                    1. Glycolysis:
                       - Occurs in the cytoplasm (cytosol).
                       - Anaerobic process (does not require oxygen).
                       - Breaks down 1 molecule of Glucose (6 carbons) into 2 molecules of Pyruvate (3 carbons).
                       - Yields a net profit of 2 ATP and 2 NADH molecules per glucose.
                       
                    2. The Krebs Cycle (Citric Acid Cycle):
                       - Occurs inside the mitochondrial matrix.
                       - Pyruvate is converted to Acetyl-CoA before entering the cycle.
                       - Produces 2 ATP, 6 NADH, 2 FADH2, and releases CO2 as a byproduct per glucose molecule.
                       
                    3. Electron Transport Chain (ETC) & Oxidative Phosphorylation:
                       - Occurs along the inner mitochondrial membrane (cristae).
                       - Aerobic process requiring oxygen as the final electron acceptor.
                       - High-energy electrons from NADH and FADH2 drive proton pumps, creating a proton gradient across the inner membrane.
                       - ATP Synthase uses chemiosmosis to produce approximately 32 to 34 ATP.
                       - Oxygen combines with protons and electrons to form water (H2O).
                       
                    KEY TOPICS TO REMEMBER:
                    - Glycolysis location: Cytoplasm.
                    - Final Electron Acceptor: Oxygen (O2).
                    - Total ATP yield per glucose: ~36-38 ATP.
                    - Anaerobic vs Aerobic respiration efficiency.
                """.trimIndent()
            )
            "CS" -> DocumentOcrResult(
                fileName = "Data_Structures_Algorithms.png",
                sourceType = "IMAGE",
                extractedText = """
                    DATA STRUCTURES & ALGORITHMIC COMPLEXITY
                    
                    1. Array vs Linked List:
                       - Array: Contiguous memory allocation. O(1) random access, O(n) insertion/deletion at arbitrary positions.
                       - Linked List: Nodes connected by pointers. O(n) random access, O(1) insertion/deletion once the position node is found.
                       
                    2. Binary Search Trees (BST):
                       - Left child < Parent node < Right child.
                       - Average time complexity for Search, Insert, and Delete is O(log n).
                       - Worst-case time complexity is O(n) if the tree becomes unbalanced (skewed tree).
                       - Self-balancing trees like AVL or Red-Black trees guarantee O(log n) worst-case time.
                       
                    3. Sorting Algorithms Comparison:
                       - QuickSort: Average O(n log n), Worst O(n^2), Space O(log n). Divide and conquer.
                       - MergeSort: Average O(n log n), Worst O(n log n), Space O(n). Stable sorting.
                       - BubbleSort: Average O(n^2), Worst O(n^2), Space O(1). Simple comparison sort.
                       
                    4. Hash Tables:
                       - Maps keys to values using a Hash Function.
                       - Ideal time complexity: O(1) average lookup, insertion, and deletion.
                       - Collision resolution techniques: Chaining (linked lists) and Open Addressing (linear probing, quadratic probing).
                """.trimIndent()
            )
            else -> DocumentOcrResult(
                fileName = "World_History_WW1_Overview.pdf",
                sourceType = "PDF",
                extractedText = """
                    WORLD HISTORY: ORIGINS OF THE FIRST WORLD WAR (1914-1918)
                    
                    The long-term causes of World War I can be remembered using the M-A-I-N acronym:
                    - Militarism: Massive arms races between European powers, particularly Germany and Great Britain's naval rivalry.
                    - Alliances: Complex system of mutual defense treaties, including the Triple Entente (Britain, France, Russia) and the Triple Alliance (Germany, Austria-Hungary, Italy).
                    - Imperialism: Competition for colonial territories in Africa and Asia created fierce friction.
                    - Nationalism: Intense national pride and Slavic nationalism in the Balkan region ("the powder keg of Europe").
                    
                    THE IMMEDIATE TRIGGER:
                    On June 28, 1914, Archduke Franz Ferdinand, heir to the Austro-Hungarian throne, was assassinated in Sarajevo by Gavrilo Princip, a member of the Black Hand Serbian nationalist group.
                    
                    THE CHAIN REACTION:
                    1. Austria-Hungary declared war on Serbia.
                    2. Russia mobilized troops to protect Serbia.
                    3. Germany declared war on Russia and France, invading neutral Belgium under the Schlieffen Plan.
                    4. Great Britain entered the war in defense of Belgian neutrality.
                    
                    TRENCH WARFARE & CONSEQUENCES:
                    - Western Front characterized by stalemate and trench warfare along France and Belgium.
                    - Treaty of Versailles (1919) imposed heavy reparations and guilt clauses on Germany.
                """.trimIndent()
            )
        }
    }
}
