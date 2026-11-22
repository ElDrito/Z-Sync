package app.zsync.core

object GameIcons {
    data class Tag(val name: String, val icon: String?)

    private const val BASE = "https://act-webstatic.hoyoverse.com/event-static-hoyowiki-admin/"

    val specialties: Map<Int, Tag> = mapOf(
        1 to Tag("Attack", BASE + "2024/05/07/78d407e2839038da13d26ad84b99a6b8_8151225937174652019.png"),
        2 to Tag("Stun", BASE + "2024/05/07/a798cde5543596aae302d7fc037df2d5_4111998480888596222.png"),
        3 to Tag("Anomaly", BASE + "2024/05/07/55982d13c3284d382a9a59ce9f5e5d50_2052082579823154975.png"),
        4 to Tag("Support", BASE + "2024/05/07/e1ce1ff7a8c3debf3235b2bbd94bd4c0_2060958246427347333.png"),
        5 to Tag("Defense", BASE + "2024/05/07/ba974570d0627f78131b5c09ffb630f2_1224778078231447651.png"),
        6 to Tag("Rupture", BASE + "2025/05/26/c0757d3edb6fe9a706a2d0431def51e6_8085324341880460509.png"),
        7 to Tag("Armorer", BASE + "2026/09/10/442942c0c06a188b19ab3fa761685018_3730761288319589198.png"),
    )

    val elements: Map<Int, Tag> = mapOf(
        200 to Tag("Physical", BASE + "2024/05/07/b34f33a07ddfae7bbd014a5e36443ea4_127526883819084638.png"),
        201 to Tag("Fire", BASE + "2024/05/07/f7bdcd7c6574c22e6c90f25cae025376_3782022421620830515.png"),
        202 to Tag("Ice", BASE + "2024/05/07/a99ef1d28678ab2e82b25099416e2802_8886922862138996075.png"),
        203 to Tag("Electric", BASE + "2024/05/07/23f0846bd60219d09ab419b831ce2de7_2106359761671844521.png"),
        204 to Tag("Wind", BASE + "2026/05/07/ca1380b043acf79edd88488daa8f684f_9103230267957946619.png"),
        205 to Tag("Ether", BASE + "2024/05/07/4b33b2be46b732ab987c6b12903bc8c4_6165206938504838110.png"),
        300 to Tag("Lumiflux", BASE + "2026/07/23/3434f5b56f2731a96cac79a625037150_8140029120562329519.png"),
    )

    val BATTERY: String? = "https://act-upload.hoyoverse.com/event-ugc-hoyowiki/2025/02/08/167256510/d863d49f931ca0dbac3b0ec563657368_2411518699554855175.png"

    const val TOPUP_URL = "https://sdk.hoyoverse.com/payment/zenless/en/index.html"
    const val TOPUP_ICON = "https://fastcdn.hoyoverse.com/static-resource-v2/2026/09/07/e41716e404c4d11051e6f947aef7beb6_171809363458945330.png"

    fun specialty(id: Int?): Tag? = id?.let { specialties[it] }

    fun element(id: Int?, subId: Int?): Tag? = id?.let { elements[it] }

    fun rankBadge(rarity: String?): String? = when (rarity) {
        "S" -> "https://img.game8.co/3857733/5ad66e91eaa162f1fe3382756e3f637b.png/show"
        "A" -> "https://img.game8.co/3924043/874d61a540955e4b38057129ff2e82e5.png/show"
        else -> null
    }
}
