#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform vec2 u_resolution;

void main() {
    vec2 px = 1.0 / u_resolution;
    vec4 c  = vec4(0.0);

    float w[3];
    w[0] = 0.2270270270;
    w[1] = 0.3162162162;
    w[2] = 0.0702702703;

    c += texture2D(u_texture, v_texCoords) * w[0];

    for (int i = 1; i <= 2; i++) {
        float fi = float(i);
        vec2 offset = vec2(px.x * fi * 2.0, px.y * fi * 2.0);
        c += texture2D(u_texture, v_texCoords + offset) * w[i];
        c += texture2D(u_texture, v_texCoords - offset) * w[i];
    }

    c.rgb *= 0.55;
    gl_FragColor = c;
}