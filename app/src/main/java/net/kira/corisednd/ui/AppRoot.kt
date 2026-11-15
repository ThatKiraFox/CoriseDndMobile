package net.kira.corisednd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import net.kira.corisednd.data.AppContainer
import net.kira.corisednd.ui.auth.AuthViewModel
import net.kira.corisednd.ui.auth.LoginScreen
import net.kira.corisednd.ui.auth.RegisterScreen
import net.kira.corisednd.ui.characters.CharacterDetailScreen
import net.kira.corisednd.ui.characters.CharacterDetailViewModel
import net.kira.corisednd.ui.characters.CharacterFormScreen
import net.kira.corisednd.ui.characters.CharacterFormViewModel
import net.kira.corisednd.ui.characters.CharactersScreen
import net.kira.corisednd.ui.characters.CharactersViewModel
import net.kira.corisednd.ui.reference.*
import net.kira.corisednd.ui.characters.CharacterWizardScreen
import net.kira.corisednd.ui.characters.CharacterWizardViewModel

private const val LOADING = "loading" // настоящий токен (JWT) таким быть не может

@Composable
fun AppRoot(container: AppContainer) {
    val token by remember { container.tokenStore.token }.collectAsState(initial = LOADING)
    val scope = rememberCoroutineScope()

    when {
        token == LOADING -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        token == null -> {
            val vm: AuthViewModel = viewModel(factory = viewModelFactory {
                initializer { AuthViewModel(container) }
            })
            val nav = rememberNavController()
            NavHost(nav, startDestination = "login") {
                composable("login") { LoginScreen(vm, onRegister = { nav.navigate("register") }) }
                composable("register") { RegisterScreen(vm, onBack = { nav.popBackStack() }) }
            }
        }
        else -> {
            val nav = rememberNavController()
            // растут после изменений: новый ключ -> новая ViewModel -> данные грузятся заново
            var listVersion by remember { mutableIntStateOf(0) }
            var detailVersion by remember { mutableIntStateOf(0) }

            NavHost(nav, startDestination = "characters") {
                composable("characters") {
                    val vm: CharactersViewModel = viewModel(
                        key = "characters-$token-$listVersion",
                        factory = viewModelFactory { initializer { CharactersViewModel(container) } },
                    )
                    CharactersScreen(
                        vm,
                        onOpen = { id -> nav.navigate("character/$id") },
                        onCreate = { nav.navigate("create") },
                        onLogout = { scope.launch { container.tokenStore.clear() } },
                        onReference = { nav.navigate("reference") },
                    )
                }
                composable("create") {
                    val vm: CharacterWizardViewModel = viewModel(
                        key = "wizard-create",
                        factory = viewModelFactory { initializer { CharacterWizardViewModel(container) } },
                    )
                    CharacterWizardScreen(
                        vm,
                        onBack = { nav.popBackStack() },
                        onSaved = { listVersion++; nav.popBackStack() },
                    )
                }

                composable("reference") {
                    ReferenceHomeScreen(
                        onBack = { nav.popBackStack() },
                        onOpen = { s -> nav.navigate("reference/${s.name}") },
                    )
                }
                composable("reference/{section}") { entry ->
                    val section = Section.valueOf(entry.arguments!!.getString("section")!!)
                    val vm: ReferenceListViewModel = viewModel(
                        key = "ref-list-${section.name}",
                        factory = viewModelFactory {
                            initializer { ReferenceListViewModel(container, section) }
                        },
                    )
                    ReferenceListScreen(
                        vm,
                        onBack = { nav.popBackStack() },
                        onOpen = { id -> nav.navigate("reference/${section.name}/$id") },
                    )
                }
                composable(
                    route = "reference/{section}/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val section = Section.valueOf(entry.arguments!!.getString("section")!!)
                    val id = entry.arguments!!.getLong("id")
                    val vm: ReferenceDetailViewModel = viewModel(
                        key = "ref-detail-${section.name}-$id",
                        factory = viewModelFactory {
                            initializer { ReferenceDetailViewModel(container, section, id) }
                        },
                    )
                    ReferenceDetailScreen(vm, onBack = { nav.popBackStack() })
                }

                composable(
                    route = "character/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments!!.getLong("id")
                    DisposableEffect(id) { onDispose { detailVersion++ } }
                    val vm: CharacterDetailViewModel = viewModel(
                        key = "character-$token-$id-$detailVersion",
                        factory = viewModelFactory {
                            initializer { CharacterDetailViewModel(container, id) }
                        },
                    )
                    CharacterDetailScreen(
                        vm,
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate("character/$id/edit") },
                        onDeleted = {
                            listVersion++
                            nav.popBackStack()
                        },
                    )
                }
                composable(
                    route = "character/{id}/edit",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments!!.getLong("id")
                    DisposableEffect(id) { onDispose { detailVersion++ } }
                    val vm: CharacterFormViewModel = viewModel(
                        key = "form-$id",
                        factory = viewModelFactory {
                            initializer { CharacterFormViewModel(container, id) }
                        },
                    )
                    CharacterFormScreen(
                        vm,
                        isEdit = true,
                        onBack = { nav.popBackStack() },
                        onSaved = { listVersion++; nav.popBackStack() },
                    )
                }
            }
        }
    }
}