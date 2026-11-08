package com.newljk415.studytimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newljk415.studytimer.domain.SessionType
import com.newljk415.studytimer.domain.TimerStatus

@Composable
fun TimerScreen(
    ui: TimerUi,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = ui.snapshot ?: return
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "${s.type.label()} 세션",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        val statusText = when (s.status) {
            TimerStatus.IDLE -> "대기 중"
            TimerStatus.RUNNING -> "진행 중"
            TimerStatus.PAUSED -> "일시정지됨"
        }
        Text(statusText, style = MaterialTheme.typography.titleMedium)
        Text(
            formatClock(ui.remainingMs),
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "남은 시간 ${spokenDuration(ui.remainingMs)}"
            },
        )
        if (s.status == TimerStatus.IDLE && s.lastCompletedType != null) {
            val msg = if (s.lastCompletedType == SessionType.FOCUS) "집중 세션을 완료했어요. 준비되면 휴식을 시작하세요."
            else "휴식이 끝났어요. 준비되면 집중을 시작하세요."
            Text(msg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            val primaryLabel = when (s.status) {
                TimerStatus.IDLE -> "${s.type.label()} 시작"
                TimerStatus.RUNNING -> "일시정지"
                TimerStatus.PAUSED -> "재개"
            }
            Button(
                onClick = if (s.status == TimerStatus.RUNNING) onPause else onStart,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text(primaryLabel) }
            OutlinedButton(
                onClick = { confirmReset = true },
                enabled = s.status != TimerStatus.IDLE,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text("초기화") }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("타이머를 초기화할까요?") },
            text = { Text("현재 ${s.type.label()} 세션을 처음으로 되돌립니다. 지금까지 실제로 집중한 시간은 기록에 남고, 완료 횟수에는 포함되지 않습니다.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }) { Text("초기화") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소") } },
        )
    }
}
