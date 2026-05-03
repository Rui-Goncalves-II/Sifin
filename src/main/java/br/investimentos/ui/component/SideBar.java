package br.investimentos.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class SideBar extends VBox {

    private final List<NavButton> navButtons = new ArrayList<>();

    public record NavItem(String icon, String label, Runnable action, boolean disabled) {
        public NavItem(String icon, String label, Runnable action) { this(icon, label, action, false); }
    }

    private static class NavButton extends Button {
        NavButton(String icon, String label, boolean disabled) {
            setText(icon);
            getStyleClass().add("nav-item");
            setMaxWidth(Double.MAX_VALUE);
            setDisable(disabled);
            Tooltip.install(this, new Tooltip(label + (disabled ? " (Em breve)" : "")));
        }

        void setActive(boolean active) {
            if (active) {
                if (!getStyleClass().contains("active")) getStyleClass().add("active");
            } else {
                getStyleClass().remove("active");
            }
        }
    }

    public SideBar() {
        getStyleClass().add("sidebar");
        setPrefWidth(56);
        setMinWidth(56);
        setMaxWidth(56);

        Label logo = new Label("S");
        logo.getStyleClass().add("nav-logo");
        VBox logoBox = new VBox(logo);
        logoBox.setAlignment(Pos.CENTER);
        logoBox.setPadding(new Insets(14, 0, 10, 0));
        getChildren().add(logoBox);
    }

    public void addItem(NavItem item) {
        NavButton btn = new NavButton(item.icon(), item.label(), item.disabled());
        if (!item.disabled() && item.action() != null) {
            btn.setOnAction(e -> {
                setActiveButton(btn);
                item.action().run();
            });
        }
        navButtons.add(btn);
        getChildren().add(btn);
    }

    public void addSeparator() {
        Region sep = new Region();
        sep.getStyleClass().add("nav-separator");
        sep.setMaxWidth(Double.MAX_VALUE);
        getChildren().add(sep);
    }

    public void addSpacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);
    }

    public void activateFirst() {
        navButtons.stream().filter(b -> !b.isDisable()).findFirst().ifPresent(this::setActiveButton);
    }

    private void setActiveButton(NavButton active) {
        navButtons.forEach(b -> b.setActive(false));
        active.setActive(true);
    }
}
