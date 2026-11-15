package io.github.hello09x.fakeplayer.core.repository.model;

import io.github.hello09x.devtools.core.utils.SingletonSupplier;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.manager.action.ActionManager;

import java.util.function.Supplier;

public interface Singletons {

    Supplier<ActionManager> actionManager = new SingletonSupplier<>(() -> Main.getInjector().getInstance(ActionManager.class));

}
