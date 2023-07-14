import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

abstract class Scene {
    public Scene() {}

    public abstract void init();

    public abstract void update(float deltaTime);
}

class EditorScene extends Scene {

    public EditorScene() {
        super();
    }

    @Override
    public void init() {

    }

    @Override
    public void update(float deltaTime) {
    }
}

class Time {
    public static float appStartTimeInNanoSeconds = System.nanoTime();

    public static float secondsSinceAppStart() {
        // delta in nano seconds then converting to seconds
        return (float)((System.nanoTime() - Time.appStartTimeInNanoSeconds) * 1E-9);
    }
}

class KeyListener {
    //
    // GLFW input guide https://www.glfw.org/docs/3.3/input_guide.html
    //

    private static KeyListener instance;

    private boolean keyPressed[] = new boolean[350];

    private KeyListener() {}

    public static KeyListener get() {
        if(KeyListener.instance == null) {
            KeyListener.instance = new KeyListener();
        }

        return KeyListener.instance;
    }

    public static void keyCallback(long windowHandle, int key, int scancode, int action, int mods) {
        if(action == GLFW_PRESS) {
            KeyListener.get().keyPressed[key] = true;
        } else if(action == GLFW_RELEASE) {
            KeyListener.get().keyPressed[key] = false;
        }
    }

    public static boolean isKeyPressed(int key) {
        return KeyListener.get().keyPressed[key];
    }
}

class MouseListener {
    //
    // GLFW input guide https://www.glfw.org/docs/3.3/input_guide.html
    //

    private static MouseListener instance;

    public double scrollX, scrollY, xPos, yPos, lastX, lastY;
    public boolean mouseButtons[] = new boolean[3];

    private MouseListener() {
        this.scrollX = 0;
        this.scrollY = 0;
        this.xPos = 0;
        this.yPos = 0;
        this.lastX = 0;
        this.lastY = 0;
    }

    public static MouseListener get() {
        if(MouseListener.instance == null) {
            MouseListener.instance = new MouseListener();
        }

        return MouseListener.instance;
    }

    public static void mousePositionCallback(long windowHandle, double xPos, double yPos) {
        MouseListener.get().lastX = MouseListener.get().xPos;
        MouseListener.get().lastY = MouseListener.get().yPos;

        MouseListener.get().xPos = xPos;
        MouseListener.get().yPos = yPos;
    }

    public static void mouseButtonCallback(long windowHandle, int button, int action, int mods) {
        if(button >= MouseListener.get().mouseButtons.length) return;

        if(action == GLFW_PRESS) {
            MouseListener.get().mouseButtons[button] = true;
        } else if(action == GLFW_RELEASE) {
            MouseListener.get().mouseButtons[button] = false;
        }
    }

    public static void mouseScrollCallback(long windowHandle, double xOffset, double yOffset) {
        MouseListener.get().scrollX = xOffset;
        MouseListener.get().scrollY = yOffset;
    }

    public static boolean isMouseButtonDown(int button) {
        return MouseListener.get().mouseButtons[button];
    }

    public static void reset() {
        MouseListener.get().scrollX = 0;
        MouseListener.get().scrollY = 0;
        MouseListener.get().lastX = MouseListener.get().xPos;
        MouseListener.get().lastY = MouseListener.get().yPos;
    }
}

class Window {
    public int width;
    public int height;
    public String title;
    public long windowHandle;
    public Scene currentScene;

    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;
        this.title = title;
        this.windowHandle = 0;
        this.currentScene = new EditorScene();
    }

    public void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if(!glfwInit()) {
            throw new IllegalStateException("Cannot init glfw");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        // glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);

        this.windowHandle = glfwCreateWindow(this.width, this.height, this.title, NULL, NULL);
        if(this.windowHandle == NULL) {
            throw new IllegalStateException("Cannot create glfw window");
        }

        glfwSetCursorPosCallback(this.windowHandle, MouseListener::mousePositionCallback);
        glfwSetMouseButtonCallback(this.windowHandle, MouseListener::mouseButtonCallback);
        glfwSetScrollCallback(this.windowHandle, MouseListener::mouseScrollCallback);
        glfwSetKeyCallback(this.windowHandle, KeyListener::keyCallback);

        glfwMakeContextCurrent(this.windowHandle);
        // v-sync on
        glfwSwapInterval(1);
        glfwShowWindow(this.windowHandle);

        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();
    }

    public void run() {
        if(this.windowHandle == NULL) return;

        float deltaTime = 0;

        currentScene.init();

        while(!glfwWindowShouldClose(this.windowHandle)) {
            float startTime = Time.secondsSinceAppStart();
            glfwPollEvents();

            glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);

            currentScene.update(deltaTime);

            glfwSwapBuffers(this.windowHandle);

            float endTime = Time.secondsSinceAppStart();
            deltaTime = endTime - startTime;
        }

        glfwFreeCallbacks(this.windowHandle);
        glfwDestroyWindow(this.windowHandle);
        glfwTerminate();
//        glfwSetErrorCallback(null).free();
    }
}

public class Main {
    public static void main(String[] args) {

        Window window = new Window(640, 420, "Game engine");
        window.init();
        window.run();
    }
}