package com.rootdb.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootdb.studio.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun Chevron(tint: Color = MiuixTheme.colorScheme.onBackgroundVariant) {
    Icon(
        imageVector = MiuixIcons.Basic.ArrowRight,
        contentDescription = null,
        modifier = Modifier.size(width = 8.dp, height = 13.dp),
        tint = tint,
    )
}

@Composable
fun BackIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = stringResource(R.string.content_desc_back),
            modifier = Modifier.size(width = 8.dp, height = 13.dp).rotate(180f),
        )
    }
}

@Composable
fun RowItem(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = MiuixTheme.colorScheme.onBackgroundVariant,
    showChevron: Boolean = true,
    subtitleMaxLines: Int = 1,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: Color = MiuixTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = iconTint,
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Text(value, fontSize = 13.sp, color = valueColor)
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Chevron()
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer
    val fg = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, fontSize = 13.sp, color = fg)
    }
}

@Composable
fun CardGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = null,
    ) {
        Column { content() }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
    }
}

@Composable
fun ButtonRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/**
 * 把 [text] 里所有与 [query] 匹配的片段（不区分大小写、可多处）加上背景色，用于搜索高亮。
 * 用 regionMatches 逐位比较，避免 lowercase() 改变长度导致区间错位（如土耳其语 İ/i）。
 */
fun highlightMatches(text: String, query: String, background: Color): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty() || text.isEmpty() || q.length > text.length) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var i = 0
        while (i <= text.length - q.length) {
            if (text.regionMatches(i, q, 0, q.length, ignoreCase = true)) {
                addStyle(SpanStyle(background = background), i, i + q.length)
                i += q.length
            } else {
                i++
            }
        }
    }
}

/**
 * 文本过长时截一段「包含首次命中位置」的窗口，保证被搜到的片段不会落在省略号之后看不见。
 * 不匹配时原样返回，交给界面按普通省略处理。
 */
fun fitAroundMatch(text: String, query: String, maxChars: Int): String {
    val q = query.trim()
    if (q.isEmpty() || text.length <= maxChars) return text
    val idx = text.indexOf(q, ignoreCase = true)
    if (idx < 0) return text
    val limit = maxOf(maxChars, q.length + 6)
    val lead = ((limit - q.length) / 2).coerceAtLeast(0)
    val start = (idx - lead).coerceAtLeast(0)
    val end = minOf(maxOf(start + limit, idx + q.length), text.length)
    return buildString {
        if (start > 0) append('…')
        append(text, start, end)
        if (end < text.length) append('…')
    }
}
