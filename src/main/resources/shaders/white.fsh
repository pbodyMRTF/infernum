#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;

void main() {
    // Doku katsayısını ve alfa değerini alıyoruz, RGB bileşenlerini 1.0 (beyaz) yapıyoruz
    vec4 texColor = texture2D(u_texture, v_texCoords);
    gl_FragColor = vec4(1.0, 1.0, 1.0, texColor.a * v_color.a);
}