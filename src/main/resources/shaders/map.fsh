#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;
varying vec4 v_color;

uniform sampler2D u_texture;
uniform float u_time;
uniform vec2 u_resolution;

// Gelişmiş noise fonksiyonu
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    vec4 texColor = texture2D(u_texture, v_texCoords);

    // Orta seviye vignette - dengeli
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(v_texCoords, center);
    float vignette = 1.0 - smoothstep(0.5, 1.2, dist);
    vignette = pow(vignette, 1.0);

    // Dikey duman/sis efekti
    float smoke = noise(vec2(v_texCoords.x * 5.0, v_texCoords.y * 3.0 - u_time * 0.1));
    smoke += noise(vec2(v_texCoords.x * 10.0, v_texCoords.y * 6.0 - u_time * 0.15)) * 0.5;

    // Yavaş nabız - tüm harita nefes alıyor gibi
    float heartbeat = 0.85 + 0.15 * sin(u_time * 1.5);

    // Kan damlası efekti - yukarıdan aşağı akan
    float bloodDrip = noise(vec2(v_texCoords.x * 20.0, v_texCoords.y * 10.0 + u_time * 0.5));
    bloodDrip = smoothstep(0.7, 0.9, bloodDrip);

    // Cehennem renk paleti
    vec3 hellRed = vec3(0.5, 0.05, 0.05);
    vec3 darkOrange = vec3(0.4, 0.1, 0.05);
    vec3 bloodRed = vec3(0.7, 0.0, 0.0);

    // Taban rengi - koyu kırmızımsı
    vec3 baseColor = mix(hellRed, darkOrange, smoke * 0.5);

    // Kan efekti ekle
    baseColor = mix(baseColor, bloodRed, bloodDrip * 0);

    // Texture ile cehennem rengini dengeli karıştır
    vec3 finalColor = texColor.rgb;

    // Cehennem tonu - orta seviye
    finalColor = mix(finalColor, finalColor * baseColor, 0.55);

    // Orta seviye heartbeat
    finalColor *= (0.92 + 0.08 * heartbeat);

    // Belirgin ama aşırı olmayan vignette
    finalColor *= (0.75 + 0.25 * vignette);

    // Orta seviye kırmızı ton
    finalColor += hellRed * 0.12 * heartbeat;

    // Belirgin titreme
    float flicker = 0.96 + 0.04 * noise(vec2(u_time * 10.0, 0.0));
    finalColor *= flicker;

    // Orta seviye grain
    float grain = (noise(v_texCoords * u_time * 50.0) - 0.5) * 0.05;
    finalColor += grain;

    // Orta seviye karanlık - cehennem ama çok değil
    finalColor *= 0.72;

    gl_FragColor = vec4(finalColor, texColor.a) * v_color;
}