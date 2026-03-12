#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;
varying vec4 v_color;

uniform sampler2D u_texture;
uniform float u_time;

void main() {
    vec4 texColor = texture2D(u_texture, v_texCoords);

    // Nabız efekti - zamanla parlaklık değişimi
    float pulse = 0.5 + 0.5 * sin(u_time * 4.0);

    // Kenar parlaması
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(v_texCoords, center);
    float glow = 5.0 - smoothstep(0.3, 0.5, dist);

    // Ana renk (kırmızı-turuncu karışımı)
    vec3 baseColor = vec3(0.9, 0.2 + pulse * 0.2, 0.1);

    // Parlaklık efekti
    vec3 finalColor = baseColor * (1.0 + glow * 0.5 * pulse);

    gl_FragColor = vec4(finalColor, texColor.a) * texColor * v_color;
}