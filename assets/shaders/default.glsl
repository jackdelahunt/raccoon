//type vertex
#version 330 core

layout (location=0) in vec3 attribute_position;
layout (location=1) in vec4 attribute_color;

out vec4 fragment_color;

void main() {
    fragment_color = attribute_color;
    gl_Position = vec4(attribute_position, 1.0);
}

//type fragment
#version 330 core

in vec4 fragment_color;
out vec4 color;

void main() {
    color = fragment_color;
}