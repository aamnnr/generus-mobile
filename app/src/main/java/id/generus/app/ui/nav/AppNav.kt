package id.generus.app.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import id.generus.app.ui.components.DUR_COLOR
import id.generus.app.ui.components.DUR_ENTER
import id.generus.app.ui.components.DUR_EXIT
import id.generus.app.ui.components.EaseOutSoft
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import id.generus.app.data.model.Me
import id.generus.app.ui.components.LocalHasBottomBar
import id.generus.app.data.model.canEditCurriculum
import id.generus.app.data.model.canReadGenerus
import id.generus.app.data.model.kurikulumEnabled
import id.generus.app.data.model.rekapRootUnit
import id.generus.app.ui.screens.admin.AdminAkunScreen
import id.generus.app.ui.screens.admin.AdminWilayahScreen
import id.generus.app.ui.screens.admin.AkunBaruScreen
import id.generus.app.ui.screens.admin.AkunDetailScreen
import id.generus.app.ui.screens.agenda.AgendaDetailScreen
import id.generus.app.ui.screens.agenda.AgendaFormScreen
import id.generus.app.ui.screens.agenda.AgendaListScreen
import id.generus.app.ui.screens.agenda.ScannerScreen
import id.generus.app.ui.screens.akun.GantiPasswordScreen
import id.generus.app.ui.screens.akun.LainnyaScreen
import id.generus.app.ui.screens.beranda.BerandaScreen
import id.generus.app.ui.screens.capaian.CapaianGenerusScreen
import id.generus.app.ui.screens.capaian.CapaianHomeScreen
import id.generus.app.ui.screens.capaian.CapaianKitabScreen
import id.generus.app.ui.screens.capaian.CapaianTargetScreen
import id.generus.app.ui.screens.capaian.RekapCapaianScreen
import id.generus.app.ui.screens.generus.GenerusDetailScreen
import id.generus.app.ui.screens.generus.GenerusFormScreen
import id.generus.app.ui.screens.generus.GenerusListScreen
import id.generus.app.ui.screens.generus.ImportGenerusScreen
import id.generus.app.ui.screens.generus.KartuScreen
import id.generus.app.ui.screens.generus.KenaikanScreen
import id.generus.app.ui.screens.kurikulum.KurikulumHomeScreen
import id.generus.app.ui.screens.kurikulum.MateriFormScreen
import id.generus.app.ui.screens.kurikulum.PeriodDetailScreen
import id.generus.app.ui.screens.kurikulum.PeriodFormScreen
import id.generus.app.ui.screens.kurikulum.TargetFormScreen
import id.generus.app.ui.screens.laporan.LaporanScreen

/** User yang sedang login; tersedia di semua layar setelah masuk. */
val LocalMe = compositionLocalOf<Me> { error("Belum login") }

data class TabItem(val route: Any, val label: String, val icon: ImageVector)

/** Menu utama per peran (padanan buildNav di src/components/shell/nav.ts, tanpa Kartu QR). */
fun mainItems(me: Me): List<TabItem> {
    val read = me.canReadGenerus
    val items = mutableListOf(TabItem(Beranda, "Beranda", Icons.Rounded.Home))
    if (read) {
        items += TabItem(AgendaList, "Agenda", Icons.Rounded.CalendarMonth)
        items += TabItem(GenerusList, "Generus", Icons.Rounded.Groups)
    }
    if (kurikulumEnabled) {
        if (read) items += TabItem(CapaianHome, "Capaian", Icons.Rounded.TaskAlt)
        else if (me.rekapRootUnit() != null || me.canEditCurriculum) items += TabItem(RekapCapaian(), "Rekap", Icons.Rounded.TaskAlt)
        items += TabItem(KurikulumHome, "Kurikulum", Icons.AutoMirrored.Rounded.MenuBook)
    }
    if (read) items += TabItem(Laporan, "Laporan", Icons.Rounded.BarChart)
    return items
}

/** Tab bawah: maksimal 4 item, selalu diakhiri "Lainnya". */
fun bottomTabs(me: Me): List<TabItem> = mainItems(me).take(3) + TabItem(Lainnya, "Lainnya", Icons.Rounded.MoreHoriz)

fun NavHostController.navigateTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun MainNav(me: Me) {
    val nav = rememberNavController()
    val tabs = bottomTabs(me)
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = tabs.any { t -> destination?.hierarchy?.any { it.hasRoute(t.route::class) } == true }

    fun isTab(e: NavBackStackEntry) = tabs.any { t -> e.destination.hierarchy.any { it.hasRoute(t.route::class) } }

    CompositionLocalProvider(LocalMe provides me, LocalHasBottomBar provides showBar) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                NavHost(
                    navController = nav,
                    startDestination = Beranda,
                    // Pindah tab: crossfade 180 ms. Buka halaman: fade + geser masuk 220 ms (DESIGN.md §9).
                    enterTransition = {
                        if (isTab(initialState) && isTab(targetState)) fadeIn(tween(DUR_COLOR))
                        else fadeIn(tween(DUR_ENTER, easing = EaseOutSoft)) + slideInHorizontally(tween(DUR_ENTER, easing = EaseOutSoft)) { it / 10 }
                    },
                    exitTransition = {
                        if (isTab(initialState) && isTab(targetState)) fadeOut(tween(DUR_COLOR))
                        else fadeOut(tween(DUR_EXIT)) + slideOutHorizontally(tween(DUR_ENTER, easing = EaseOutSoft)) { -it / 20 }
                    },
                    popEnterTransition = { fadeIn(tween(DUR_ENTER, easing = EaseOutSoft)) + slideInHorizontally(tween(DUR_ENTER, easing = EaseOutSoft)) { -it / 20 } },
                    popExitTransition = { fadeOut(tween(DUR_EXIT)) + slideOutHorizontally(tween(DUR_ENTER, easing = EaseOutSoft)) { it / 10 } },
                ) {
                    composable<Beranda> { BerandaScreen(nav) }
                    composable<AgendaList> { AgendaListScreen(nav) }
                    composable<GenerusList> { GenerusListScreen(nav) }
                    composable<CapaianHome> { CapaianHomeScreen(nav) }
                    composable<KurikulumHome> { KurikulumHomeScreen(nav) }
                    composable<Laporan> { LaporanScreen(nav) }
                    composable<Lainnya> { LainnyaScreen(nav) }

                    composable<AgendaDetail> { AgendaDetailScreen(nav, it.toRoute<AgendaDetail>().id) }
                    composable<AgendaForm> { AgendaFormScreen(nav, it.toRoute<AgendaForm>().id) }
                    composable<Scanner> { ScannerScreen(nav, it.toRoute<Scanner>().agendaId) }

                    composable<GenerusDetail> { GenerusDetailScreen(nav, it.toRoute<GenerusDetail>().id) }
                    composable<GenerusForm> { GenerusFormScreen(nav, it.toRoute<GenerusForm>().id) }
                    composable<Kartu> { val r = it.toRoute<Kartu>(); KartuScreen(nav, r.unitId, r.generusId) }
                    composable<Kenaikan> { KenaikanScreen(nav) }
                    composable<ImportGenerus> { ImportGenerusScreen(nav) }

                    composable<CapaianTarget> { val r = it.toRoute<CapaianTarget>(); CapaianTargetScreen(nav, r.targetId, r.kelompokId) }
                    composable<CapaianKitab> { val r = it.toRoute<CapaianKitab>(); CapaianKitabScreen(nav, r.targetId, r.kelompokId) }
                    composable<CapaianGenerus> { val r = it.toRoute<CapaianGenerus>(); CapaianGenerusScreen(nav, r.generusId, r.periodId) }
                    composable<RekapCapaian> { val r = it.toRoute<RekapCapaian>(); RekapCapaianScreen(nav, r.unitId, r.periodId, r.jenjangId) }

                    composable<PeriodDetail> { PeriodDetailScreen(nav, it.toRoute<PeriodDetail>().id) }
                    composable<PeriodForm> { PeriodFormScreen(nav, it.toRoute<PeriodForm>().id) }
                    composable<TargetForm> { val r = it.toRoute<TargetForm>(); TargetFormScreen(nav, r.periodId, r.jenjangId, r.targetId) }
                    composable<MateriForm> { MateriFormScreen(nav, it.toRoute<MateriForm>().id) }

                    composable<AdminWilayah> { AdminWilayahScreen(nav) }
                    composable<AdminAkun> { AdminAkunScreen(nav) }
                    composable<AkunDetail> { AkunDetailScreen(nav, it.toRoute<AkunDetail>().id) }
                    composable<AkunBaru> { AkunBaruScreen(nav) }
                    composable<GantiPassword> { GantiPasswordScreen(onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() }) }
                }
            }
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        }
    }
}
