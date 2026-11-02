package com.appscz.cbfinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.appscz.cbfinance.data.DbManager
import com.appscz.cbfinance.ui.AppRoot
import com.appscz.cbfinance.ui.CBTheme
import com.appscz.cbfinance.ui.ThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DbManager.init(this)
        ThemeState.load(this)
        setContent {
            CBTheme {
                AppRoot()
            }
        }
    }
}
