package com.arjun.gander.epub

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import java.io.File
import java.io.IOException
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

data class EpubLibraryMetadata(
    val title: String?,
    val cover: Bitmap?,
)

object EpubLibraryMetadataReader {
    suspend fun read(context: Context, file: File): Result<EpubLibraryMetadata> = runCatching {
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val publicationParser = DefaultPublicationParser(
            context = context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        )
        val publicationOpener = PublicationOpener(publicationParser)
        val asset = assetRetriever.retrieve(file.toUrl()).getOrElse { error ->
            throw IOException("Unable to retrieve EPUB asset: $error")
        }
        val publication = publicationOpener.open(
            asset,
            allowUserInteraction = false,
        ).getOrElse { error ->
            asset.close()
            throw IOException("Unable to open EPUB publication: $error")
        }

        try {
            EpubLibraryMetadata(
                title = publication.metadata.title.takeIf { it.isNotBlank() },
                cover = publication.coverFitting(Size(COVER_WIDTH, COVER_HEIGHT)),
            )
        } finally {
            publication.close()
        }
    }

    private const val COVER_WIDTH = 600
    private const val COVER_HEIGHT = 900
}
