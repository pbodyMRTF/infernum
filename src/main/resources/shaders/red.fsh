#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;
varying vec4 v_color;

uniform sampler2D u_texture;
uniform float u_time;
uniform float u_health;

void main() {
    vec4 texColor = texture2D(u_texture, v_texCoords);

    // Nabız efekti
    float pulse = 0.5 + 0.5 * sin(u_time * 4.0);

    // Merkeze uzaklık
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(v_texCoords, center);

    // Glow
    float glow = 1.0 - smoothstep(0.3, 0.5, dist);

    // Ana renk
    float danger = 1.0 - u_health; // az can = daha fazla kırmızı
    vec3 baseColor = vec3(0.9 + danger * 0.1, 0.2 + pulse * 0.2 - danger * 0.15, 0.1);

    // Parlaklık
    vec3 finalColor = baseColor * (1.0 + glow * 0.9 * pulse);

    // Clamp
    finalColor = clamp(finalColor, 0.0, 1.0);


    gl_FragColor = vec4(finalColor * texColor.rgb, texColor.a) * v_color;
}