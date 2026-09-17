package com.arjun.gander.epub

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.IOException
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.allAreHtml
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Owns one opened EPUB publication for the lifetime of the reader screen.
 * Persistent library metadata deliberately stores only JSON strings and primitives,
 * so Readium types never leak into the storage contract.
 */
class EpubReaderSession private constructor(
    val publication: Publication,
    val navigatorFactory: EpubNavigatorFactory,
    val initialLocator: Locator?,
) : Closeable {

    override fun close() {
        publication.close()
    }

    companion object {
        suspend fun open(
            context: Context,
            file: File,
            locatorJson: String?,
        ): Result<EpubReaderSession> = runCatching {
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

            val isEpub = publication.conformsTo(Publication.Profile.EPUB) ||
                publication.readingOrder.allAreHtml
            if (!isEpub) {
                publication.close()
                throw IOException("Imported publication is not EPUB-compatible")
            }

            val initialLocator = locatorJson
                ?.takeIf { it.isNotBlank() }
                ?.let { json -> runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull() }

            EpubReaderSession(
                publication = publication,
                navigatorFactory = EpubNavigatorFactory(publication),
                initialLocator = initialLocator,
            )
        }
    }
}
