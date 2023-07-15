import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryUtil.*;

class GameObject {
    public String name;
    public ArrayList<Component> components;

    public GameObject(String name) {
        this.name = name;
        this.components = new ArrayList<Component>();
    }

    public void start() {
        for (Component component : this.components) {
            component.start();
        }
    }

    public void update(float deltaTime) {
        for (Component component : this.components) {
            component.update(deltaTime);
        }
    }

    @Nullable
    public <T extends Component> T getComponent(Class<T> component) {
        for(Component c : this.components) {
            if(c.getClass().isAssignableFrom(component)) {
                return component.cast(c);
            }
        }

        return null;
    }

    public <T extends Component> void removeComponent(Class<T> component) {
        for(int i = 0; i < this.components.size(); i++) {
            Component c = this.components.get(i);
            if(c.getClass().isAssignableFrom(component)) {
                this.components.remove(i);
                break;
            }
        }
    }

    public void addComponent(Component component) {
        this.components.add(component);
        component.gameObject = this;
    }
}

abstract class Component {
    GameObject gameObject = null;

    public abstract void start();
    public abstract void update(float deltaTime);
}

class Texture {
    public String filePath;
    public int textureId;

    public Texture(String filePath) {
        this.filePath = filePath;

        this.textureId = glGenTextures();
        this.bind();

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT); // repeat in X
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT); // repeat in Y

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); // nearest neighbour sampling (pixelated), when stretching
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST); // nearest neighbour sampling (pixelated), when shrinking

        IntBuffer width = BufferUtils.createIntBuffer(1);
        IntBuffer height = BufferUtils.createIntBuffer(1);
        IntBuffer channels = BufferUtils.createIntBuffer(1);
        stbi_set_flip_vertically_on_load(true); // orientate it the correct way
        ByteBuffer imageBuffer = stbi_load(this.filePath, width, height, channels, 0); // these int buffers get set by stbi when reading the texture
        assert imageBuffer != null : "Failed to load image " + this.filePath;

        int image_type = 0;
        if(channels.get(0) == 3) {
            image_type = GL_RGB;
        } else if(channels.get(0) == 4) {
            image_type = GL_RGBA;
        } else {
            assert false : "Unknown texture format";
        }

        glTexImage2D(GL_TEXTURE_2D, 0, image_type, width.get(0), height.get(0), 0, image_type, GL_UNSIGNED_BYTE, imageBuffer);

        stbi_image_free(imageBuffer);
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, this.textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }
}

class Camera {
    public Vector2f position;
    public Matrix4f projectionMatrix, viewMatrix;

    public Camera(Vector2f position) {
        this.position = position;
        this.projectionMatrix = new Matrix4f();
        this.viewMatrix = new Matrix4f();

        this.adjustProjection();
    }

    public void adjustProjection() {
        projectionMatrix.identity();
        projectionMatrix.ortho(0f, 640f, 0f, 480f, 0, 100);
    }

    public Matrix4f getViewMatrix() {
        Vector3f cameraFront = new Vector3f(0f, 0f, -1f); // where the camera is looking
        Vector3f cameraUp = new Vector3f(0f, 1f, 0f);
        this.viewMatrix.identity();
        this.viewMatrix.lookAt(
            new Vector3f(this.position.x, this.position.y, 20f),    // where the camera is
            cameraFront.add(this.position.x, this.position.y, 0f),  // where the camera is looking
            cameraUp                                                   // also just needs the up vector
        );

        return this.viewMatrix;
    }
}

class Shader {
    public int shaderProgramId;
    public String vertexShaderSource;
    public String fragmentShaderSource;
    public int vertexShaderId;
    public int fragmentShaderId;

    public Shader(String vertexShaderSource, String fragmentShaderSource) {
        this.vertexShaderSource = vertexShaderSource;
        this.fragmentShaderSource = fragmentShaderSource;
    }

    public void compile() {
        // load and compile vertex shader
        this.vertexShaderId = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(this.vertexShaderId, this.vertexShaderSource);
        glCompileShader(this.vertexShaderId);

        // check for errors
        int success_vertex = glGetShaderi(this.vertexShaderId, GL_COMPILE_STATUS);
        if(success_vertex == GL_FALSE) {
            int length = glGetShaderi(this.vertexShaderId, GL_INFO_LOG_LENGTH);
            System.out.println("Error compiling vertex shader");
            String errorMessage = glGetShaderInfoLog(this.vertexShaderId, length);
            System.out.println(":: Error message ::\n" + errorMessage + "\n");
            assert false: "Right now we are failing on shader compilation failures";
        }

        // load and compile fragment shader
        this.fragmentShaderId = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(this.fragmentShaderId, this.fragmentShaderSource);
        glCompileShader(this.fragmentShaderId);

        // check for errors
        int success_fragment = glGetShaderi(this.fragmentShaderId, GL_COMPILE_STATUS);
        if(success_fragment == GL_FALSE) {
            int length = glGetShaderi(this.fragmentShaderId, GL_INFO_LOG_LENGTH);
            System.out.println("Error compiling fragment shader");
            String errorMessage = glGetShaderInfoLog(this.fragmentShaderId, length);
            System.out.println(":: Error message ::\n" + errorMessage);
            assert false: "Right now we are failing on shader compilation failures";
        }

        // creating and linking shaders to program
        this.shaderProgramId = glCreateProgram();
        glAttachShader(this.shaderProgramId, this.vertexShaderId);
        glAttachShader(this.shaderProgramId, this.fragmentShaderId);
        glLinkProgram(this.shaderProgramId);

        // check for errors
        int success_shader = glGetProgrami(this.shaderProgramId, GL_LINK_STATUS);
        if(success_shader == GL_FALSE) {
            int length = glGetProgrami(this.shaderProgramId, GL_INFO_LOG_LENGTH);
            System.out.println("Error compiling linking shader program");
            String errorMessage = glGetShaderInfoLog(this.shaderProgramId, length);
            System.out.println(":: Error message ::\n" + errorMessage);
            assert false: "Right now we are failing on shader compilation failures";
        }
    }

    public void bind() {
        glUseProgram(this.shaderProgramId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public void uniformMatrix4Float(String variableName, Matrix4f matrix) {
        this.bind();
        int location = glGetUniformLocation(this.shaderProgramId, variableName);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16 /*    4 * 4   */);
        matrix.get(buffer);
        glUniformMatrix4fv(location, false, buffer);
    }

    public void uniformFloat(String variableName, float value) {
        this.bind();
        int location = glGetUniformLocation(this.shaderProgramId, variableName);
        glUniform1f(location, value);
    }

    public void uniformTextureSlot(String variableName, int slot) {
        this.bind();
        int location = glGetUniformLocation(this.shaderProgramId, variableName);
        glUniform1i(location, slot);
    }
}

abstract class Scene {
    public ArrayList<GameObject> gameObjects;

    public Scene() {
        this.gameObjects = new ArrayList<>();
    }

    public abstract void start();
    public abstract void update(float deltaTime);
}

class EditorScene extends Scene {

    public String vertexShaderSource = "#version 330 core\n" +
            "\n" +
            "layout (location=0) in vec3 attribute_position;\n" +
            "layout (location=1) in vec4 attribute_color;\n" +
            "layout (location=2) in vec2 attribute_uv_coords;\n" +
            "\n" +
            "uniform mat4 uniform_projection_matrix;\n" +
            "uniform mat4 uniform_view_matrix;\n" +
            "\n" +
            "out vec4 fragment_color;\n" +
            "out vec2 fragment_uv_coords;\n" +
            "\n" +
            "void main() {\n" +
            "    fragment_color = attribute_color;\n" +
            "    fragment_uv_coords = attribute_uv_coords;\n" +
            "    gl_Position = uniform_projection_matrix * uniform_view_matrix * vec4(attribute_position, 1.0);\n" +
            "}";

    public String fragmentShaderSource = "#version 330 core\n" +
            "\n" +
            "uniform sampler2D uniform_texture;\n" +
            "\n" +
            "in vec4 fragment_color;\n" +
            "in vec2 fragment_uv_coords;\n" +
            "out vec4 color;\n" +
            "\n" +
            "void main() {\n" +
            "    color = texture(uniform_texture, fragment_uv_coords);\n" +
            "}";

    public int vertexAttributeId, vertexBufferId, elementBufferId;
    public Shader shader;
    public Texture texture;
    public Camera camera;

    public float[] vertexArray = {
            // positions            // colors               // UV coords
            100f, 0f, 0f,           1.0f, 0f, 0f, 1f,       1f, 0f,             // bottom right
            0f, 0f, 0f,             0f, 1f, 0f, 1f,         0f, 0f,             // bottom left
            0f, 100f, 0f,           0f, 0f, 1f, 1f,         0f, 1f,             // top left
            100f, 100f, 0f,         0f, 1f, 0f, 1f,         1f, 1f              // top right
    };

    public int[] elementArray = {
            // counterclockwise order
            0, 2, 1,
            0, 2, 3
    };

    public EditorScene() {
        super();

        this.camera = new Camera(new Vector2f(0f, 0f));
        this.texture = new Texture("assets/textures/raccoon.png");
    }

    @Override
    public void start() {

        this.shader = new Shader(vertexShaderSource, fragmentShaderSource);
        this.shader.compile();

        // create the vao, vbo and ebo
        this.vertexAttributeId = glGenVertexArrays();
        glBindVertexArray(this.vertexAttributeId);

        // making and binding the vertex buffer
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(this.vertexArray.length);
        vertexBuffer.put(this.vertexArray).flip();
        this.vertexBufferId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, this.vertexBufferId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        // making and binding the element buffer
        IntBuffer elementBuffer = BufferUtils.createIntBuffer(this.elementArray.length);
        elementBuffer.put(this.elementArray).flip();
        this.elementBufferId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.elementBufferId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, elementBuffer, GL_STATIC_DRAW);

        // add it all to the vertex attribute object
        int positionSize = 3;
        int colorSize = 4;
        int UVSize = 2;
        int vertexSizeInBytes = (positionSize + colorSize + UVSize) * Float.BYTES;

        glVertexAttribPointer(0, positionSize, GL_FLOAT, false, vertexSizeInBytes, NULL);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, colorSize, GL_FLOAT, false, vertexSizeInBytes, positionSize * Float.BYTES);
        glEnableVertexAttribArray(1);

        glVertexAttribPointer(2, UVSize, GL_FLOAT, false, vertexSizeInBytes, (positionSize + colorSize) * Float.BYTES);
        glEnableVertexAttribArray(2);

        for (GameObject gameObject : this.gameObjects) {
            gameObject.start();
        }
    }

    @Override
    public void update(float deltaTime) {
        this.shader.bind();

        this.shader.uniformTextureSlot("uniform_texture", 0);
        glActiveTexture(GL_TEXTURE0);
        this.texture.bind();

        this.shader.uniformMatrix4Float("uniform_projection_matrix", camera.projectionMatrix);
        this.shader.uniformMatrix4Float("uniform_view_matrix", camera.getViewMatrix());

        glBindVertexArray(this.vertexAttributeId);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);

        glDrawElements(GL_TRIANGLES, elementArray.length, GL_UNSIGNED_INT, 0);

        for (GameObject gameObject : this.gameObjects) {
            gameObject.update(deltaTime);
        }

        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glBindVertexArray(0);

        this.shader.unbind();
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
        this.currentScene = null;
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

        this.currentScene = new EditorScene();
        this.currentScene.start();

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

        Window window = new Window(960, 640, "Game engine");
        window.init();
        window.run();
    }
}