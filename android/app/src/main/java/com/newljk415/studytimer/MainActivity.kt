package com.newljk415.studytimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newljk415.studytimer.ui.MainViewModel
import com.newljk415.studytimer.ui.StatsScreen
import com.newljk415.studytimer.ui.TimerScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels { MainViewModel.Factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val timer by vm.timer.collectAsStateWithLifecycle()
                val days by vm.last7Days.collectAsStateWithLifecycle()
                Scaffold(bottomBar = {
                    NavigationBar {
                        listOf("타이머", "기록").forEachIndexed { i, label ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i },
                                icon = { Text(if (i == 0) "⏱" else "📊") }, label = { Text(label) })
                        }
                    }
                }) { pad ->
                    when (tab) {
                        0 -> TimerScreen(timer, vm::startOrResume, vm::pause, vm::reset, Modifier.padding(pad))
                        else -> StatsScreen(days, Modifier.padding(pad))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}
