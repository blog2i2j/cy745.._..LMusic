package com.lalilu.component.base

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NavigatorHeader(
    modifier: Modifier = Modifier,
    @StringRes titleRes: Int,
    @StringRes subTitleRes: Int,
    titleScale: Float = 1f,
    paddingValues: PaddingValues = PaddingValues(
        top = 26.dp,
        bottom = 20.dp,
        start = 20.dp,
        end = 20.dp
    ),
    extraContent: @Composable RowScope.() -> Unit = {}
) = NavigatorHeader(
    modifier = modifier,
    title = stringResource(id = titleRes),
    subTitle = stringResource(id = subTitleRes),
    paddingValues = paddingValues,
    titleScale = titleScale,
    extraContent = extraContent
)

@Composable
fun NavigatorHeader(
    modifier: Modifier = Modifier,
    title: String,
    subTitle: String,
    titleScale: Float = 1f,
    paddingValues: PaddingValues = PaddingValues(
        top = 26.dp,
        bottom = 20.dp,
        start = 20.dp,
        end = 20.dp
    ),
    extraContent: @Composable RowScope.() -> Unit = {}
) = NavigatorHeader(
    modifier = modifier,
    title = title,
    titleScale = titleScale,
    rowExtraContent = extraContent,
    paddingValues = paddingValues,
    columnExtraContent = {
        if (subTitle.isNotBlank()) {
            Text(
                text = subTitle,
                fontSize = 14.sp,
                color = contentColorFor(backgroundColor = MaterialTheme.colors.background)
                    .copy(alpha = 0.5f)
            )
        }
    }
)

@Composable
fun NavigatorHeader(
    modifier: Modifier = Modifier,
    title: String,
    titleScale: Float = 1f,
    paddingValues: PaddingValues = PaddingValues(
        top = 26.dp,
        bottom = 20.dp,
        start = 20.dp,
        end = 20.dp
    ),
    columnExtraSpace: Dp = 15.dp,
    rowExtraSpace: Dp = 20.dp,
    columnExtraContent: @Composable ColumnScope.() -> Unit = {},
    rowExtraContent: @Composable RowScope.() -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(rowExtraSpace),
        modifier = modifier.padding(paddingValues)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(columnExtraSpace)
        ) {
            Text(
                text = title,
                fontSize = 26.sp * titleScale,
                lineHeight = 26.sp * titleScale * 1.5f,
                color = contentColorFor(backgroundColor = MaterialTheme.colors.background)
            )
            columnExtraContent()
        }
        rowExtraContent()
    }
}
