package com.example.mebeluchet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("orders")
private val ORDERS_KEY = stringPreferencesKey("orders_json")

@Serializable
data class Order(
    val id: Long,
    val name: String,
    val title: String,
    val total: Double,
    val deposit: Double,
    val cutting: Double,
    val parts: Double,
    val expenses: Double,
    val master: Double
) {
    val clientBalance get() = total - deposit
    val totalExpenses get() = cutting + parts + expenses + master
    val income get() = total - totalExpenses
}

class OrderViewModel(private val context: Context) : androidx.lifecycle.ViewModel() {
    var orders by mutableStateOf<List<Order>>(emptyList())
        private set

    init {
        androidx.lifecycle.viewModelScope.launch {
            val raw = context.dataStore.data.first()[ORDERS_KEY]
            if (!raw.isNullOrBlank()) orders = runCatching { Json.decodeFromString(raw) }.getOrDefault(emptyList())
        }
    }

    fun save(order: Order) {
        orders = (orders.filterNot { it.id == order.id } + order).sortedByDescending { it.id }
        androidx.lifecycle.viewModelScope.launch {
            context.dataStore.edit { it[ORDERS_KEY] = Json.encodeToString(orders) }
        }
    }

    fun delete(order: Order) {
        orders = orders.filterNot { it.id == order.id }
        androidx.lifecycle.viewModelScope.launch {
            context.dataStore.edit { it[ORDERS_KEY] = Json.encodeToString(orders) }
        }
    }
}

@Composable
fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true
    )
}

fun Double.money(): String = "%,.0f".format(this).replace(',', ' ')

@Composable
fun App(vm: OrderViewModel) {
    var editing by remember { mutableStateOf<Order?>(null) }
    var showNew by remember { mutableStateOf(false) }

    if (showNew || editing != null) {
        OrderForm(
            initial = editing,
            onBack = { showNew = false; editing = null },
            onSave = { vm.save(it); showNew = false; editing = null }
        )
        return
    }

    val total = vm.orders.sumOf { it.total }
    val received = vm.orders.sumOf { it.deposit }
    val balance = vm.orders.sumOf { it.clientBalance }
    val income = vm.orders.sumOf { it.income }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Учёт мебели") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                SummaryCard("Заказов", vm.orders.size.toString())
                SummaryCard("Сумма заказов", "${total.money()} сум")
                SummaryCard("Получено", "${received.money()} сум")
                SummaryCard("Остаток клиентов", "${balance.money()} сум")
                SummaryCard("Доход", "${income.money()} сум")
                Spacer(Modifier.height(8.dp))
                Text("Заказы", style = MaterialTheme.typography.titleLarge)
            }
            items(vm.orders) { order ->
                ElevatedCard(onClick = { editing = order }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(order.name, style = MaterialTheme.typography.titleMedium)
                        Text(order.title, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Заказ: ${order.total.money()} сум")
                        Text("Остаток: ${order.clientBalance.money()} сум")
                        Text("Расходы: ${order.totalExpenses.money()} сум")
                        Text("Доход: ${order.income.money()} сум")
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun SummaryCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun OrderForm(initial: Order?, onBack: () -> Unit, onSave: (Order) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var total by remember { mutableStateOf(initial?.total?.toString() ?: "") }
    var deposit by remember { mutableStateOf(initial?.deposit?.toString() ?: "") }
    var cutting by remember { mutableStateOf(initial?.cutting?.toString() ?: "") }
    var parts by remember { mutableStateOf(initial?.parts?.toString() ?: "") }
    var expenses by remember { mutableStateOf(initial?.expenses?.toString() ?: "") }
    var master by remember { mutableStateOf(initial?.master?.toString() ?: "") }

    fun num(s: String) = s.replace(",", ".").toDoubleOrNull() ?: 0.0
    val t = num(total); val d = num(deposit); val c = num(cutting); val p = num(parts); val e = num(expenses); val m = num(master)
    val balance = t - d
    val allExpenses = c + p + e + m
    val income = t - allExpenses

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Новый заказ" else "Изменить заказ") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            item {
                OutlinedTextField(name, { name = it }, label = { Text("Имя клиента") }, modifier = Modifier.fillMaxWidth().padding(vertical=4.dp), singleLine=true)
                OutlinedTextField(title, { title = it }, label = { Text("Название заказа") }, modifier = Modifier.fillMaxWidth().padding(vertical=4.dp), singleLine=true)
                MoneyField("Сумма заказа", total) { total = it }
                MoneyField("Заклад", deposit) { deposit = it }
                MoneyField("Оплата на распил", cutting) { cutting = it }
                MoneyField("Оплата на запчасти", parts) { parts = it }
                MoneyField("Расходы", expenses) { expenses = it }
                MoneyField("Оплата мастеру", master) { master = it }

                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Расчёт", style = MaterialTheme.typography.titleMedium)
                        Text("Остаток от клиента: ${balance.money()} сум")
                        Text("Общие расходы: ${allExpenses.money()} сум")
                        Text("Доход: ${income.money()} сум")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank() && title.isNotBlank()) {
                            onSave(Order(initial?.id ?: System.currentTimeMillis(), name, title, t, d, c, p, e, m))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить заказ") }

                if (initial != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val vm: OrderViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return OrderViewModel(applicationContext) as T
                    }
                })
                App(vm)
            }
        }
    }
}
