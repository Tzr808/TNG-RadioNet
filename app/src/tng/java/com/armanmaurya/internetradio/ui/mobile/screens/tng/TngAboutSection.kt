package com.armanmaurya.internetradio.ui.mobile.screens.tng

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R

/**
 * TNG-specific AboutSection composable.
 * Replaces the default AboutSection when building with the "tng" flavor.
 * Shows a simplified dark card with branding, version info, and GitHub link.
 */
@Composable
fun TngAboutSection(
    modifier: Modifier = Modifier,
    versionName: String,
) {
    val context = LocalContext.current
    val githubUrl = "https://github.com/armanmaurya/InternetRadio"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App name
            Text(
                text = context.getString(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Version line
            Text(
                text = context.getString(R.string.tng_about_version_format, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Attribution text with clickable GitHub link
            val basedOnText = context.getString(R.string.tng_about_based_on)
            val authorText = context.getString(R.string.tng_about_author)
            val availableOnText = context.getString(R.string.tng_about_available_on)
            val githubText = context.getString(R.string.tng_about_github)

            val annotatedString = buildAnnotatedString {
                val beforeGitHub = "$basedOnText $authorText\n$availableOnText "
                append(beforeGitHub)
                append(githubText)
                addStringAnnotation(
                    tag = "github_url",
                    annotation = githubUrl,
                    start = beforeGitHub.length,
                    end = length
                )
            }

            Text(
                text = annotatedString,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Silently ignore
                        }
                    }
            )
        }
    }
}
