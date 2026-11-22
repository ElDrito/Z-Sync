import app.zsync.core.*
import kotlin.test.Test

class LiveSmokeTest {
    private val fake = Account("123456789", mapOf("ltoken_v2" to "v2_x", "ltuid_v2" to "123456789", "ltmid_v2" to "abc_hy",
        "cookie_token_v2" to "v2_y", "account_id_v2" to "123456789", "account_mid_v2" to "abc_hy"))
    private val api = Api()

    @Test fun smoke() {
        if (System.getenv("LIVE") != "1") return
        for (g in Game.entries) println("codes ${g.name}: " + api.fetchCodes(g).map { it.code })
        println("roles: " + api.fetchRoles(fake).second.let { "${it.status} ${it.retcode} ${it.message}" })
        for (g in Game.entries) println("checkin ${g.name}: " + api.checkIn(fake, g))
        for (g in listOf(Game.ZZZ)) {
            val role = Role(g, "1000000000", "x", if (g == Game.ZZZ) "prod_gf_us" else if (g == Game.GENSHIN) "os_usa" else "prod_official_usa")
            println("notes ${g.name}: " + api.notes(fake, role).second.let { "${it.status} ${it.retcode} ${it.message}" })
            println("redeem ${g.name}: " + api.redeem(fake, role, "TESTCODE123"))
        }
        println("stoken: " + Auth.tokensFromStoken("v2_fake", "abc_hy"))
        println("login: " + Auth.login("nobody-zz-test@example.invalid", "wrong-password-1", Auth.newDeviceId()))
        println("mimo: " + Mimo { "es-es" }.run(fake, Role(Game.ZZZ, "1000000000", "x", "prod_gf_us"), MimoOptions()) { RedeemResult(false, null, "", false, false) })
    }
}

class ExtrasSmokeTest {
    @Test fun endpoints() {
        if (System.getenv("LIVE") != "1") return
        val q = mapOf("uid" to "1000000000", "region" to "prod_gf_us", "role_id" to "1000000000", "server" to "prod_gf_us", "schedule_type" to "1")
        val h = Http.labHeaders("es-es") + mapOf("DS" to Http.ds(), "Cookie" to "ltoken_v2=v2_x; ltuid_v2=1; ltmid_v2=a")
        for (host in listOf("sg-act-public-api", "sg-act-nap-api")) for (e in listOf("note", "cur_gacha_detail", "activity_calendar", "gacha_calendar", "hadal_info_v2", "hadal_mem_detail_v2", "abysss2_abstract", "no_such_endpoint")) {
            val r = Http.request("GET", "https://$host.hoyolab.com/event/game_record_zzz/api/zzz/$e", q, h)
            println("ep $host $e: ${r.status} ${r.retcode} ${r.message}")
        }
        val r = Http.request("GET", "https://sg-public-api.hoyolab.com/event/nap_ledger/month_info", q + ("month" to ""), h)
        println("ep ledger: ${r.status} ${r.retcode} ${r.message}")
    }
}

class Game8SmokeTest {
    @Test fun upcoming() {
        if (System.getenv("LIVE") != "1") return
        val g = Game8.fetch(null)
        println("g8 version=${g?.version} items=${g?.items?.size}")
        g?.items?.forEach { println("g8 item $it") }
        println("assault=${FandomWiki.assaultCycle(java.time.LocalDate.parse("2026-11-06"))}")
        val us = Role(Game.ZZZ, "1000000", "x", "prod_gf_us", 60)
        for (d in listOf("2026-10-23", "2026-11-06", "2026-11-20")) println("lineup $d=${AssaultLineup.forCycle(us, java.time.LocalDate.parse(d))}")
        val again = Game8.fetch(g)
        println("g8 cached=${again?.items == g?.items}")
        val r = "prod_gf_us"
        println("reset daily=${java.time.Instant.ofEpochMilli(Resets.nextDaily(r))} weekly=${java.time.Instant.ofEpochMilli(Resets.nextWeekly(r))}")
    }
}
