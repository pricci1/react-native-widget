package expo.modules.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.google.gson.annotations.SerializedName
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

class WidgetModule : Module() {
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('Widget')` in JavaScript.
    Name("Widget")

    // Sets constant properties on the module. Can take a dictionary or a closure that returns a dictionary.
    Constants(
      "PI" to Math.PI
    )

    // Defines event names that the module can send to JavaScript.
    Events("onChange")

    // Defines a JavaScript synchronous function that runs the native code on the JavaScript thread.
    Function("hello") {
      "Hello world! 👋"
    }

    // Defines a JavaScript function that always returns a Promise and whose native code
    // is by default dispatched on the different thread than the JavaScript runtime runs on.
    AsyncFunction("setValueAsync") { value: String ->
      // Send an event to JavaScript.
      sendEvent("onChange", mapOf(
        "value" to value
      ))
    }

    // Enables the module to be used as a native view. Definition components that are accepted as part of
    // the view definition: Prop, Events.
    View(WidgetView::class) {
      // Defines a setter for the `name` prop.
      Prop("name") { view: WidgetView, prop: String ->
        println(prop)
      }
    }
  }
}

data class Ticker(
  @SerializedName("market_id")
  val marketId: String,
  @SerializedName("price_variation_24h")
  val priceVariation24h: String,
  @SerializedName("price_variation_7d")
  val priceVariation7d: String,
  @SerializedName("last_price")
  val lastPrice: List<String>
)
data class TickerResponse(val tickers: List<Ticker>)

interface BudaAPI {
  @GET("tickers")
  suspend fun fetchTickers(): TickerResponse
}

object API {
  private const val BASE_URL ="https://www.buda.com/api/v2/"
  private val retrofit: Retrofit = Retrofit.Builder().baseUrl(BASE_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

  val budaAPI: BudaAPI by lazy {
    retrofit.create(BudaAPI::class.java)
  }

  private var _currentTickers = MutableStateFlow<List<Ticker>>(listOf())
  val currentTickers: StateFlow<List<Ticker>> get() = _currentTickers

  suspend fun updateTickers() {
    try {
      _currentTickers.value = budaAPI.fetchTickers().tickers
      println(_currentTickers.value)
    } catch (e: Exception) {
      println("Something went wrong with the tickers fetch")
      println(e.message)
    }
  }
}

class MyAppWidget : GlanceAppWidget() {
  override suspend fun provideGlance(context: Context, id: GlanceId) {
    // In this method, load data needed to render the AppWidget.
    // Use `withContext` to switch to another thread for long running
    // operations.

    provideContent {
      MyContent()
    }
  }
  @Composable
  private fun MyContent() {

    val tickers by API.currentTickers.collectAsState()

//    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateTickers: () -> Unit = { scope.launch { API.updateTickers() } }

    val firstTicker = if (tickers.size > 0) tickers[0] else null

    Column(
      modifier = GlanceModifier.fillMaxSize(),
      verticalAlignment = Alignment.Top,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(text = "Where to?", modifier = GlanceModifier.padding(12.dp))
      Row(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
          text = "${firstTicker?.marketId}",
          onClick = {
            updateTickers()
          }
        )
        Button(
          text = "${firstTicker?.lastPrice?.get(0)}",
          onClick = {
            updateTickers()
          }
        )
      }
    }
  }
}

class MyAppWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = MyAppWidget()

  override fun onEnabled(context: Context?) {
    super.onEnabled(context)
    CoroutineScope(Dispatchers.IO).launch {
      API.updateTickers()
    }
  }
}
