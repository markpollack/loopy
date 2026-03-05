package io.github.markpollack.loopy;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.github.markpollack.loopy", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	// Forge layer must not depend on agent internals
	@ArchTest
	static final ArchRule forge_does_not_depend_on_agent = noClasses().that()
		.resideInAPackage("io.github.markpollack.loopy.forge..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("io.github.markpollack.loopy.agent..")
		.allowEmptyShould(true)
		.because("forge classes are deterministic and must not depend on the agent layer");

	// Command layer must not depend on agent internals
	@ArchTest
	static final ArchRule command_does_not_depend_on_agent = noClasses().that()
		.resideInAPackage("io.github.markpollack.loopy.command..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("io.github.markpollack.loopy.agent..")
		.allowEmptyShould(true)
		.because("slash commands interact via CommandContext, not agent internals");

	// Agent layer must not depend on TUI
	@ArchTest
	static final ArchRule agent_does_not_depend_on_tui = noClasses().that()
		.resideInAPackage("io.github.markpollack.loopy.agent..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("io.github.markpollack.loopy.tui..")
		.allowEmptyShould(true)
		.because("agent layer is headless and must not know about the TUI");

	// Forge layer must not depend on TUI
	@ArchTest
	static final ArchRule forge_does_not_depend_on_tui = noClasses().that()
		.resideInAPackage("io.github.markpollack.loopy.forge..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("io.github.markpollack.loopy.tui..")
		.allowEmptyShould(true)
		.because("forge classes are deterministic and must not depend on the TUI layer");

	// No upstream harness imports should remain after vendoring
	@ArchTest
	static final ArchRule no_upstream_harness_imports = noClasses().that()
		.resideInAPackage("io.github.markpollack.loopy..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("org.springaicommunity.agents.harness..")
		.allowEmptyShould(true)
		.because("MiniAgent is vendored — no upstream harness imports should remain");

	// No upstream forge imports should remain after vendoring
	@ArchTest
	static final ArchRule no_upstream_forge_imports = noClasses().that()
		.resideInAPackage("io.github.markpollack.loopy..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("com.markpollack.forge..")
		.allowEmptyShould(true)
		.because("forge classes are vendored — no upstream forge imports should remain");

}
