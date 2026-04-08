package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object VerificationColors {
    val LimeGreen = Color(0xFF32CD32)
    val LimeGreenBg = Color(0xFFE8F8E8)
    val RedBadge = Color(0xFFFF3B30)
    val RedBadgeBg = Color(0xFFFDE8E7)
}

@Composable
fun VerificationBadge(verified: Boolean, modifier: Modifier = Modifier) {
    val bgColor = if (verified) VerificationColors.LimeGreenBg else VerificationColors.RedBadgeBg
    val fgColor = if (verified) VerificationColors.LimeGreen else VerificationColors.RedBadge
    val icon = if (verified) Icons.Default.CheckCircle else Icons.Default.Warning
    val label = if (verified) "VERIFIED" else "NOT VERIFIED"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = fgColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = fgColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
