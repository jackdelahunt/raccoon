#version 330 core

uniform sampler2D uniform_texture;

in vec4 fragment_color;
in vec2 fragment_uv_coords;
out vec4 color;

void main() {
    color = texture(uniform_texture, fragment_uv_coords);
//    color = fragment_color;
}