import org.slf4j.event.Level

plugins {
    id("dev.prism")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

val minecraftVersion = prop("minecraft_version")

group = prop("mod_group_id")
version = prop("mod_version")

prism {
    curseMaven()
    modrinthMaven()
    maven("Create", "https://maven.createmod.net")
    maven("IThundxr", "https://maven.ithundxr.dev/snapshots")
    maven("IllusiveSoulworks", "https://maven.theillusivec4.top/")
    maven("BlameJared", "https://maven.blamejared.com/")
    maven("SquidDev", "https://maven.squiddev.cc")
    maven("RyanHCode", "https://maven.ryanhcode.dev/releases")

    metadata {
        modId = prop("mod_id")
        name = prop("mod_name")
        description = prop("mod_description")
        license = prop("mod_license")
        author(prop("mod_authors"))
        expand("minecraft_version_range", prop("minecraft_version_range"))
        expand("neoforge_version_range", prop("neo_version_range"))
        expand("sable_version_range", prop("sable_version_range"))
    }

    version(minecraftVersion) {
        parchmentMinecraftVersion = prop("parchment_minecraft_version")
        parchmentMappingsVersion = prop("parchment_mappings_version")

        neoforge {
            loaderVersion = prop("neo_version")

            dependencies {
                implementation("curse.maven:ftb-chunks-forge-314906:9594476")
                implementation("curse.maven:architectury-api-419699:9608861")
                implementation("curse.maven:ftb-teams-forge-404468:9434081")
                implementation("curse.maven:ftb-library-forge-404465:9650381")

                runtimeOnly("curse.maven:create-328085:7963363")
                runtimeOnly("curse.maven:sable-1312371:9043865")
                runtimeOnly("maven.modrinth:create-aeronautics:1.3.2+mc1.21.1")
                runtimeOnly("curse.maven:jei-238222:9393327")
                runtimeOnly("curse.maven:mezzconfig-1689768:9527937")
            }

            runs {
                client("player1") {
                    programArg("--username")
                    programArg("Player1")
                    runDir = "run-player1"
                }
                client("player2") {
                    programArg("--username")
                    programArg("Player2")
                    runDir = "run-player2"
                }
            }

            rawNeoForge {
                runs.named("client") { gameDirectory.set(file("run")) }
                runs.named("server") {
                    gameDirectory.set(file("run"))
                    programArgument("--nogui")
                }
                runs.create("gameTestServer") { type.set("gameTestServer") }
                runs.configureEach {
                    systemProperty("forge.logging.markers", "REGISTRIES")
                    logLevel.set(Level.DEBUG)
                }
            }

            rawProject {
                dependencies {
                    listOf(
                        "com.simibubi.create:create-$minecraftVersion:${prop("create_version")}",
                        "net.createmod.ponder:ponder-neoforge:${prop("ponder_version")}+mc$minecraftVersion",
                        "net.createmod.catnip:Catnip-NeoForge-$minecraftVersion:${prop("catnip_version")}",
                        "com.tterrag.registrate:Registrate:${prop("registrate_version")}",
                        "dev.engine-room.flywheel:flywheel-neoforge-api-$minecraftVersion:${prop("flywheel_version")}",
                        "dev.ryanhcode.sable:sable-neoforge-$minecraftVersion:${prop("sable_version")}",
                        "dev.ryanhcode.sable-companion:sable-companion-common-$minecraftVersion:${prop("sable_companion_version")}",
                        "dev.simulated_team.simulated:simulated-neoforge-$minecraftVersion:${prop("simulated_version")}",
                        "dev.eriksonn.aeronautics:aeronautics-neoforge-$minecraftVersion:${prop("aeronautics_version")}",
                        "foundry.veil:veil-neoforge-$minecraftVersion:${prop("veil_version")}",
                    ).forEach { notation ->
                        add("compileOnly", notation) { (this as ExternalModuleDependency).isTransitive = false }
                    }
                }
            }

            publishingDependencies {
                curseforge {
                    requires("create-aeronautics")
                    requires("ftb-chunks-forge")
                    requires("ftb-teams-forge")
                }
                modrinth {
                    requires("create-aeronautics")
                }
            }
        }
    }

    publishing {
        changelogFile = "changelog.md"

        curseforge {
            accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
            projectId = prop("curseforge_id")
        }

        if (prop("modrinth_id").isNotBlank()) {
            modrinth {
                accessToken = providers.environmentVariable("MODRINTH_TOKEN")
                projectId = prop("modrinth_id")
            }
        }

        github {
            repository = prop("github_repo")
            tagName = "${prop("mod_version")}-$minecraftVersion"
            commitish = "master"
        }
    }
}
