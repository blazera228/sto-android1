package ru.sto.orders

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

data class Order(
    val id: Long,
    val client: String,
    val car: String,
    val details: String,
    val parts: Double,
    val work: Double
) {
    val total: Double get() = parts + work
}

class MainActivity : Activity() {
    private val orders = mutableListOf<Order>()
    private lateinit var list: LinearLayout
    private val prefs by lazy { getSharedPreferences("sto_orders", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadOrders()
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun render() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
            setBackgroundColor(0xFFF3F4F6.toInt())
        }
        scroll.addView(page)
        setContentView(scroll)

        page.addView(TextView(this).apply {
            text = "СТО — Учёт заказов"
            textSize = 24f
            setTextColor(0xFF111827.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        page.addView(TextView(this).apply {
            text = "Клиенты, автомобили и стоимость работ"
            textSize = 14f
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, dp(4), 0, dp(16))
        })
        page.addView(Button(this).apply {
            text = "+ Новый заказ"
            setOnClickListener { showNewOrderDialog() }
        })
        page.addView(TextView(this).apply {
            text = "Заказы (${orders.size})"
            textSize = 19f
            setTextColor(0xFF111827.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(20), 0, dp(10))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        refreshList()
    }

    private fun refreshList() {
        list.removeAllViews()
        if (orders.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Пока нет заказов. Нажмите «Новый заказ», чтобы добавить первый."
                textSize = 15f
                setTextColor(0xFF6B7280.toInt())
                setPadding(dp(14), dp(18), dp(14), dp(18))
                setBackgroundColor(0xFFFFFFFF.toInt())
            })
        }
        orders.sortedByDescending { it.id }.forEach { order ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(10))
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            card.addView(TextView(this).apply {
                text = order.client
                textSize = 18f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
            })
            card.addView(TextView(this).apply {
                text = order.car + "\n" + order.details +
                    "\nЗапчасти: ${money(order.parts)} ₽   Работа: ${money(order.work)} ₽" +
                    "\nИтого: ${money(order.total)} ₽"
                textSize = 14f
                setTextColor(0xFF4B5563.toInt())
                setPadding(0, dp(6), 0, dp(6))
            })
            card.addView(Button(this).apply {
                text = "Удалить заказ"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("Удалить заказ клиента ${order.client}?")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Удалить") { _, _ ->
                            orders.removeAll { it.id == order.id }
                            saveOrders()
                            refreshList()
                        }.show()
                }
            })
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(10))
                addView(card)
            }
            list.addView(wrapper)
        }
    }

    private fun showNewOrderDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        fun field(hint: String): EditText {
            val input = EditText(this).apply {
                this.hint = hint
                textSize = 16f
                singleLine = hint != "Запчасти и описание работ"
            }
            form.addView(input)
            return input
        }
        val client = field("Имя клиента")
        val car = field("Автомобиль")
        val details = field("Запчасти и описание работ")
        val parts = field("Стоимость запчастей, ₽")
        parts.inputType = 8194
        val work = field("Стоимость работ, ₽")
        work.inputType = 8194
        val dialogScroll = ScrollView(this).apply { addView(form) }
        AlertDialog.Builder(this)
            .setTitle("Новый заказ")
            .setView(dialogScroll)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = client.text.toString().trim()
                        val vehicle = car.text.toString().trim()
                        if (name.isEmpty() || vehicle.isEmpty()) {
                            Toast.makeText(this, "Укажите имя клиента и автомобиль", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val p = parts.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                        val w = work.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                        if (p < 0 || w < 0) {
                            Toast.makeText(this, "Стоимость не может быть отрицательной", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        orders.add(Order(System.currentTimeMillis(), name, vehicle,
                            details.text.toString().trim(), p, w))
                        saveOrders()
                        refreshList()
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun money(value: Double) = String.format(java.util.Locale("ru", "RU"), "%.2f", value)

    private fun saveOrders() {
        val array = JSONArray()
        orders.forEach {
            array.put(JSONObject().apply {
                put("id", it.id); put("client", it.client); put("car", it.car)
                put("details", it.details); put("parts", it.parts); put("work", it.work)
            })
        }
        prefs.edit().putString("orders", array.toString()).apply()
    }

    private fun loadOrders() {
        val array = runCatching { JSONArray(prefs.getString("orders", "[]")) }.getOrElse { JSONArray() }
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            orders.add(Order(o.optLong("id"), o.optString("client"), o.optString("car"),
                o.optString("details"), o.optDouble("parts"), o.optDouble("work")))
        }
    }
}
