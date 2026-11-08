package com.newljk415.studytimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.newljk415.studytimer.domain.DailyStat
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(days: List<DailyStat>, modifier: Modifier = Modifier) {
    val fmt = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("최근 7일 기록", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(
            "합계 ${formatFocus(days.sumOf { it.focusMs })} · 완료 ${days.sumOf { it.completedFocusSessions }}회",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "실제 집중 시간(일시정지 제외)은 자정 기준 지역 날짜별로 나눠 기록됩니다. 진행 중인 구간은 일시정지·초기화·종료·완료 시 반영됩니다.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        days.asReversed().forEach { d ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(d.date.format(fmt), style = MaterialTheme.typography.bodyLarge)
                Text("${formatFocus(d.focusMs)} · ${d.completedFocusSessions}회", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
