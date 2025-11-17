package com.gbaoperator.plugin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gbaoperator.plugin.emulator.ScaleMode
import com.gbaoperator.plugin.emulator.Shader
import com.gbaoperator.plugin.emulator.VideoConfig

/**
 * Video configuration selector component for emulator launch
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoConfigSelector(
    videoConfig: VideoConfig,
    onConfigChange: (VideoConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentConfig = videoConfig
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Video Enhancement",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Scale Mode Selection
            ScaleModeSelector(
                selectedMode = currentConfig.scaleMode,
                onModeSelect = { mode ->
                    onConfigChange(currentConfig.copy(scaleMode = mode))
                }
            )
            
            // Shader Selection
            ShaderSelector(
                selectedShader = currentConfig.shader,
                onShaderSelect = { shader ->
                    onConfigChange(currentConfig.copy(shader = shader))
                }
            )
            
            // Additional Options
            AdditionalOptions(
                maintainAspectRatio = currentConfig.maintainAspectRatio,
                onAspectRatioChange = { maintain ->
                    onConfigChange(currentConfig.copy(maintainAspectRatio = maintain))
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaleModeSelector(
    selectedMode: ScaleMode,
    onModeSelect: (ScaleMode) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Scaling Mode",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ScaleMode.values()) { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeSelect(mode) },
                    label = { 
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = if (selectedMode == mode) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShaderSelector(
    selectedShader: Shader,
    onShaderSelect: (Shader) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Video Filter",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(getCommonShaders()) { shader ->
                FilterChip(
                    selected = selectedShader == shader,
                    onClick = { onShaderSelect(shader) },
                    label = { 
                        Text(
                            text = shader.displayName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = if (selectedShader == shader) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        {
                            Icon(
                                imageVector = getShaderIcon(shader),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdditionalOptions(
    maintainAspectRatio: Boolean,
    onAspectRatioChange: (Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Additional Options",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text("Maintain Aspect Ratio")
            }
            
            Switch(
                checked = maintainAspectRatio,
                onCheckedChange = onAspectRatioChange
            )
        }
    }
}

/**
 * Get commonly used shaders for UI display
 */
private fun getCommonShaders(): List<Shader> = listOf(
    Shader.NONE,
    Shader.BILINEAR,
    Shader.HQ2X,
    Shader.HQ3X,
    Shader.XBR_2X,
    Shader.XBR_3X
)

/**
 * Get appropriate icon for each shader type
 */
private fun getShaderIcon(shader: Shader): ImageVector = when (shader) {
    Shader.NONE -> Icons.Default.Close
    Shader.BILINEAR -> Icons.Default.FilterVintage
    Shader.HQ2X, Shader.HQ3X, Shader.HQ4X -> Icons.Default.Settings
    Shader.XBR_2X, Shader.XBR_3X, Shader.XBR_4X -> Icons.Default.Tune
    Shader.SUPER_EAGLE -> Icons.Default.Star
    Shader.SCALE2X -> Icons.Default.ZoomOut
}

@Preview(showBackground = true)
@Composable
private fun VideoConfigSelectorPreview() {
    MaterialTheme {
        VideoConfigSelector(
            videoConfig = VideoConfig(),
            onConfigChange = {}
        )
    }
}