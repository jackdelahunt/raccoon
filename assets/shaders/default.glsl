//type vertex
#version 330 core

layout (location=0) in vec3 attribute_position;
layout (location=1) in vec4 attribute_color;
layout (location=2) in vec2 attribute_uv_coords;

uniform mat4 uniform_projection_matrix;
uniform mat4 uniform_view_matrix;

out vec4 fragment_color;
out vec2 fragment_uv_coords;

void main() {
    fragment_color = attribute_color;
    fragment_uv_coords = attribute_uv_coords;
    gl_Position = uniform_projection_matrix * uniform_view_matrix * vec4(attribute_position, 1.0);
}

//type fragment
#version 330 core

uniform sampler2D uniform_texture;

in vec4 fragment_color;
in vec2 fragment_uv_coords;
out vec4 color;

void main() {
    color = texture(uniform_texture, fragment_uv_coords);
}