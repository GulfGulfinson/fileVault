package com.filevault.util;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Utility-Klasse zum Erstellen und Anwenden von Animationen in der Anwendung.
 */
public class AnimationUtil {
    
    /**
     * Erstellt eine Fade-In-Animation für ein Node.
     * 
     * @param node Das zu animierende Node
     * @param duration Dauer in Millisekunden
     * @return Die konfigurierte Transition
     */
    public static FadeTransition createFadeInTransition(Node node, double duration) {
        FadeTransition fadeIn = new FadeTransition(Duration.millis(duration), node);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);
        return fadeIn;
    }
    
    /**
     * Erstellt eine Fade-Out-Animation für ein Node.
     * 
     * @param node Das zu animierende Node
     * @param duration Dauer in Millisekunden
     * @return Die konfigurierte Transition
     */
    public static FadeTransition createFadeOutTransition(Node node, double duration) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(duration), node);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setInterpolator(Interpolator.EASE_IN);
        return fadeOut;
    }
    
    /**
     * Erstellt eine Scale-In-Animation für ein Node.
     * 
     * @param node Das zu animierende Node
     * @param duration Dauer in Millisekunden
     * @return Die konfigurierte Transition
     */
    public static ScaleTransition createScaleInTransition(Node node, double duration) {
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(duration), node);
        scaleIn.setFromX(0.95);
        scaleIn.setFromY(0.95);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);
        scaleIn.setInterpolator(Interpolator.EASE_OUT);
        return scaleIn;
    }
    
    /**
     * Erstellt eine Slide-Up-Animation für ein Node.
     * 
     * @param node Das zu animierende Node
     * @param duration Dauer in Millisekunden
     * @return Die konfigurierte Transition
     */
    public static TranslateTransition createSlideUpTransition(Node node, double duration) {
        TranslateTransition slideUp = new TranslateTransition(Duration.millis(duration), node);
        slideUp.setFromY(20);
        slideUp.setToY(0);
        slideUp.setInterpolator(Interpolator.EASE_OUT);
        return slideUp;
    }
    
    /**
     * Erstellt eine Rotationsanimation für ein Node.
     * 
     * @param node Das zu animierende Node
     * @param duration Dauer in Millisekunden
     * @param cycles Anzahl der Zyklen (-1 für unendlich)
     * @return Die konfigurierte Transition
     */
    public static RotateTransition createRotateTransition(Node node, double duration, int cycles) {
        RotateTransition rotate = new RotateTransition(Duration.millis(duration), node);
        rotate.setFromAngle(0);
        rotate.setToAngle(360);
        rotate.setCycleCount(cycles);
        rotate.setInterpolator(Interpolator.LINEAR);
        return rotate;
    }
    
    /**
     * Wendet eine kombinierte Fade-In- und Scale-In-Animation auf ein Node an.
     * 
     * @param node Das zu animierende Node
     * @param duration Dauer in Millisekunden
     */
    public static void applyFadeInWithScale(Node node, double duration) {
        node.setOpacity(0);
        
        FadeTransition fadeIn = createFadeInTransition(node, duration);
        ScaleTransition scaleIn = createScaleInTransition(node, duration);
        
        ParallelTransition parallelTransition = new ParallelTransition(fadeIn, scaleIn);
        parallelTransition.play();
    }
    
    /**
     * Wendet eine kombinierte Fade-In- und Slide-Up-Animation auf ein Node an.
     * 
     * @param node Das zu animierende Node
     * @param duration Dauer in Millisekunden
     */
    public static void applyFadeInWithSlideUp(Node node, double duration) {
        node.setOpacity(0);
        
        FadeTransition fadeIn = createFadeInTransition(node, duration);
        TranslateTransition slideUp = createSlideUpTransition(node, duration);
        
        ParallelTransition parallelTransition = new ParallelTransition(fadeIn, slideUp);
        parallelTransition.play();
    }
    
    /**
     * Erstellt eine Puls-Animation für ein Node.
     * 
     * @param node Das zu animierende Node
     * @param duration Dauer in Millisekunden
     * @return Die konfigurierte Transition
     */
    public static ScaleTransition createPulseTransition(Node node, double duration) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(duration), node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.05);
        pulse.setToY(1.05);
        pulse.setCycleCount(2);
        pulse.setAutoReverse(true);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        return pulse;
    }
    
    /**
     * Erstellt eine Transition zwischen zwei Ansichten, wobei eine ausgeblendet und die andere eingeblendet wird.
     * 
     * @param currentView Die aktuelle Ansicht, die ausgeblendet wird
     * @param newView Die neue Ansicht, die eingeblendet wird
     * @param duration Dauer in Millisekunden
     * @return Die konfigurierte sequentielle Transition
     */
    public static SequentialTransition createViewTransition(Node currentView, Node newView, double duration) {
        newView.setOpacity(0);
        
        FadeTransition fadeOutCurrent = createFadeOutTransition(currentView, duration / 2);
        PauseTransition pause = new PauseTransition(Duration.millis(duration / 10));
        FadeTransition fadeInNew = createFadeInTransition(newView, duration / 2);
        
        SequentialTransition sequentialTransition = new SequentialTransition(fadeOutCurrent, pause, fadeInNew);
        return sequentialTransition;
    }
    
    /**
     * Erstellt eine Glow-Effekt-Animation für ein Node.
     * 
     * @param node Das zu animierende Node
     * @param duration Dauer in Millisekunden
     * @param cycles Anzahl der Zyklen
     */
    public static void applyGlowPulse(Node node, double duration, int cycles) {
        if (!(node instanceof Region)) {
            return;
        }
        
        Region region = (Region) node;
        String originalStyle = region.getStyle();
        String glowStyle = originalStyle + "-fx-effect: dropshadow(gaussian, #6A4BAF, 20, 0.8, 0, 0);";
        
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, 
                new KeyValue(region.styleProperty(), originalStyle)
            ),
            new KeyFrame(Duration.millis(duration / 2),
                new KeyValue(region.styleProperty(), glowStyle)
            ),
            new KeyFrame(Duration.millis(duration),
                new KeyValue(region.styleProperty(), originalStyle)
            )
        );
        
        timeline.setCycleCount(cycles);
        timeline.play();
    }
    
    /**
     * Erstellt eine Lade-Spinner-Animation.
     * 
     * @param node Das zu animierende Node als Spinner
     * @return Die konfigurierte Rotations-Transition
     */
    public static RotateTransition createLoadingSpinner(Node node) {
        RotateTransition rotate = createRotateTransition(node, 1500, -1);
        rotate.play();
        return rotate;
    }
    
    /**
     * Stoppt eine Lade-Spinner-Animation.
     * 
     * @param rotateTransition Die zu stoppende Rotations-Transition
     */
    public static void stopLoadingSpinner(RotateTransition rotateTransition) {
        if (rotateTransition != null) {
            rotateTransition.stop();
        }
    }
} 