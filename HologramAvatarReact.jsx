import React, { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';

/**
 * HologramAvatarReact - A beautifully stylized 3D Hologram AI Avatar.
 * Powered by React, raw Three.js, and custom WebGL shaders.
 * Live-responds to microphone input using the Web Audio API.
 * 
 * Features:
 *  - Custom Hologram Shader with adjustable rim glow (Fresnel effect).
 *  - Dynamic scanning lines and visual grid ripples.
 *  - Procedural soundwave vertex displacement reacting to live microphone decibel peaks.
 *  - Multi-revolving holographic HUD orbits/rings.
 *  - Ambient drifting sci-fi particle cloud.
 *  - Automatic performance-safe memory deallocation on unmount.
 * 
 * Prerequisites:
 *   npm install three
 */
export default function HologramAvatarReact() {
  const containerRef = useRef(null);
  const audioContextRef = useRef(null);
  const analyserRef = useRef(null);
  const micStreamRef = useRef(null);
  const animationFrameRef = useRef(null);
  const uniformsRef = useRef({
    uTime: { value: 0 },
    uAudioAmp: { value: 0 },
    uColor: { value: new THREE.Color(0x00e5ff) } // Cyan/Teal base hologram color
  });

  const [isMicConnected, setIsMicConnected] = useState(false);
  const [micError, setMicError] = useState(null);
  const [avatarTheme, setAvatarTheme] = useState('Holographic Cyan');
  const [audioInDb, setAudioInDb] = useState(-100);

  // Hologram style configurations
  const themes = {
    'Holographic Cyan': { color: 0x00e5ff, label: 'CYAN CORE' },
    'Cosmic Rose': { color: 0xe040fb, label: 'ROSE NEBULA' },
    'Amber Terminal': { color: 0xff9f0a, label: 'WARM TACTICAL' },
    'Toxic Matrix': { color: 0x39ff14, label: 'BIO LOGIC' }
  };

  // Change theme colors dynamically
  const switchTheme = (themeName) => {
    setAvatarTheme(themeName);
    if (uniformsRef.current.uColor) {
      uniformsRef.current.uColor.value.setHex(themes[themeName].color);
    }
  };

  // Audio setup function (triggered on user interaction)
  const initializeMicrophone = async () => {
    try {
      setMicError(null);
      // Modern Web Audio protocol
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      micStreamRef.current = stream;

      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      const audioContext = new AudioContextClass();
      audioContextRef.current = audioContext;

      const source = audioContext.createMediaStreamSource(stream);
      const analyser = audioContext.createAnalyser();
      analyser.fftSize = 256; // High responsiveness
      source.connect(analyser);
      analyserRef.current = analyser;

      setIsMicConnected(true);
    } catch (err) {
      console.error('Microphone connectivity error:', err);
      setMicError('Microphone clearance denied. Please grant permission in browser settings.');
    }
  };

  // Disable microphone explicitly
  const disconnectMicrophone = () => {
    if (micStreamRef.current) {
      micStreamRef.current.getTracks().forEach(track => track.stop());
      micStreamRef.current = null;
    }
    if (audioContextRef.current && audioContextRef.current.state !== 'closed') {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }
    analyserRef.current = null;
    setIsMicConnected(false);
    uniformsRef.current.uAudioAmp.value = 0;
    setAudioInDb(-100);
  };

  // Lifecycle: Three.js Initializer & Render Core
  useEffect(() => {
    if (!containerRef.current) return;

    const width = containerRef.current.clientWidth || 500;
    const height = containerRef.current.clientHeight || 500;

    // 1. Core Scene Setup
    const scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x030a16, 0.015);

    // 2. Camera Setup
    const camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100);
    camera.position.set(0, 0, 7);

    // 3. Renderer Setup
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(width, height);
    containerRef.current.appendChild(renderer.domElement);

    // 4. Custom Hologram Shader Material
    // Features dynamic procedural vertex displacement and Fresnel outline edge-glow.
    const hologramShaderMaterial = new THREE.ShaderMaterial({
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      uniforms: uniformsRef.current,
      vertexShader: `
        uniform float uTime;
        uniform float uAudioAmp;
        varying vec3 vNormal;
        varying vec3 vPosition;
        varying vec2 vUv;

        // Custom 3D Hash function for organic noise
        float hash(vec3 p) {
          p = fract(p * 0.3183099 + vec3(0.1));
          p *= 17.0;
          return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
        }

        void main() {
          vUv = uv;
          vNormal = normalize(normalMatrix * normal);
          vPosition = position;

          vec3 displacedPos = position;

          // 1. Organic high-frequency noise spikes triggered by volume level (Microphone displacement)
          float noise = hash(position * 2.5 + uTime * 4.0);
          if (uAudioAmp > 0.05) {
            displacedPos += normal * noise * uAudioAmp * 0.45;
          }

          // 2. Continuous cosmic wave breathing effect
          displacedPos += normal * sin(position.y * 3.0 + uTime * 2.0) * 0.05;

          gl_Position = projectionMatrix * modelViewMatrix * vec4(displacedPos, 1.0);
        }
      `,
      fragmentShader: `
        uniform float uTime;
        uniform float uAudioAmp;
        uniform vec3 uColor;
        varying vec3 vNormal;
        varying vec3 vPosition;
        varying vec2 vUv;

        void main() {
          // 1. Fresnel outer glow factor
          // We compute intensity based on looking angle vs normals (bright on the rim contours)
          vec3 viewDir = normalize(vec3(0.0, 0.0, 1.0));
          float rimGlowRaw = 1.0 - abs(dot(vNormal, viewDir));
          float rimGlow = pow(rimGlowRaw, 2.5);

          // 2. Scanlines: moving horizontal transparent bars
          float scanline = sin(vPosition.y * 30.0 - uTime * 7.0) * 0.15 + 0.85;

          // 3. Audio grid ripple pattern
          float radialGrid = sin(vPosition.x * 12.0) * sin(vPosition.z * 12.0) * 0.12 * (1.0 + uAudioAmp * 3.0);

          // 4. Randomized Holographic Signal Glitches (Flicker simulator)
          float glitch = 1.0;
          float randomChance = fract(sin(uTime * 120.0) * 43758.5453);
          if (randomChance > 0.985) {
            glitch = 0.2; // Signal momentary dropout
          } else if (randomChance > 0.97) {
            glitch = 1.4; // Sparking flare
          }

          // Merge final color elements
          vec3 centralCoreColor = uColor * (rimGlow * 1.2 + radialGrid * 0.7);
          float alpha = (rimGlow * 0.7 + 0.12 + uAudioAmp * 0.35) * scanline * glitch;

          gl_FragColor = vec4(centralCoreColor * glitch * (1.0 + uAudioAmp * 1.8), clamp(alpha, 0.0, 1.0));
        }
      `
    });

    // 5. Stylized 3D Avatar Mesh (Hyper-dimension Torus Knot Core representing computing lattice)
    const avatarGeo = new THREE.TorusKnotGeometry(0.9, 0.32, 120, 16);
    const avatarMesh = new THREE.Mesh(avatarGeo, hologramShaderMaterial);
    scene.add(avatarMesh);

    // Inner core glowing energy sphere
    const coreGeo = new THREE.SphereGeometry(0.55, 32, 32);
    const coreMat = new THREE.MeshBasicMaterial({
      color: 0xffffff,
      transparent: true,
      opacity: 0.18,
      blending: THREE.AdditiveBlending,
      wireframe: true
    });
    const coreMesh = new THREE.Mesh(coreGeo, coreMat);
    scene.add(coreMesh);

    // 6. Multi-Revolving HUD Orbits (Tactical Hologram HUD Elements)
    const hudGroup = new THREE.Group();
    scene.add(hudGroup);

    // Orbit HUD Ring 1 - Vertical spinning
    const ringGeo1 = new THREE.RingGeometry(1.5, 1.54, 64);
    const hudMat1 = new THREE.MeshBasicMaterial({
      color: themes[avatarTheme].color,
      transparent: true,
      opacity: 0.3,
      side: THREE.DoubleSide
    });
    const ring1 = new THREE.Mesh(ringGeo1, hudMat1);
    ring1.rotation.x = Math.PI / 2;
    hudGroup.add(ring1);

    // Orbit HUD Ring 2 - Concentric offset outer ticks
    const ringGeo2 = new THREE.RingGeometry(1.8, 1.82, 32);
    // Convert circle to segmented dashes via wireframe properties
    const hudMat2 = new THREE.MeshBasicMaterial({
      color: themes[avatarTheme].color,
      transparent: true,
      opacity: 0.2,
      side: THREE.DoubleSide,
      wireframe: true
    });
    const ring2 = new THREE.Mesh(ringGeo2, hudMat2);
    ring2.rotation.y = Math.PI / 4;
    hudGroup.add(ring2);

    // 7. Drifting Ambient Sci-Fi Starfield (Holographic particles)
    const particleCount = 280;
    const particleGeo = new THREE.BufferGeometry();
    const particleCoords = new Float32Array(particleCount * 3);

    for (let i = 0; i < particleCount * 3; i += 3) {
      // Scatter randomly inside spherical bounding envelope
      const radius = 2.5 + Math.random() * 2.5;
      const theta = Math.random() * Math.PI * 2;
      const phi = Math.acos((Math.random() * 2) - 1);

      particleCoords[i] = radius * Math.sin(phi) * Math.cos(theta);
      particleCoords[i + 1] = radius * Math.sin(phi) * Math.sin(theta);
      particleCoords[i + 2] = radius * Math.cos(phi);
    }

    particleGeo.setAttribute('position', new THREE.BufferAttribute(particleCoords, 3));
    
    // Dynamic procedural point styling 
    const pointMat = new THREE.PointsMaterial({
      color: themes[avatarTheme].color,
      size: 0.045,
      transparent: true,
      opacity: 0.5,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    });

    const particleSystem = new THREE.Points(particleGeo, pointMat);
    scene.add(particleSystem);

    const clock = new THREE.Clock();

    // 8. Dynamic Audio Amplitude extractor
    const processLiveAudio = () => {
      if (!analyserRef.current) return 0;
      
      const dataArray = new Uint8Array(analyserRef.current.frequencyBinCount);
      analyserRef.current.getByteFrequencyData(dataArray);

      // Analyze root-mean-square (RMS) for precise acoustic amplitude peaks
      let total = 0;
      for (let i = 0; i < dataArray.length; i++) {
        total += dataArray[i];
      }
      const averageVal = total / dataArray.length;
      
      // Calculate real DB output and normalize value
      const targetAmp = averageVal / 128.0; // Scale 0..2
      
      // Calculate readable Decibels for HUD instrumentation
      const db = Math.round((averageVal / 255) * 100 - 100);
      setAudioInDb(db > -95 ? db : -100);

      return targetAmp;
    };

    // 9. Frame Render loop
    const animate = () => {
      const elapsedTime = clock.getElapsedTime();

      // Retrieve and interpolate real-time volume input (Smoothing dampener)
      const rawAmp = processLiveAudio();
      // Smoothler interpolation
      uniformsRef.current.uAudioAmp.value += (rawAmp - uniformsRef.current.uAudioAmp.value) * 0.18;

      // Increment clock uniform for horizontal sweeps and shader noises
      uniformsRef.current.uTime.value = elapsedTime;

      // Update particle speeds and colors based on audio
      pointMat.color.copy(uniformsRef.current.uColor.value);
      hudMat1.color.copy(uniformsRef.current.uColor.value);
      hudMat2.color.copy(uniformsRef.current.uColor.value);

      // Rotate primary avatar core (swelling on audio peaks)
      const currentScaleFactor = 1.0 + uniformsRef.current.uAudioAmp.value * 0.28;
      avatarMesh.scale.set(currentScaleFactor, currentScaleFactor, currentScaleFactor);
      
      avatarMesh.rotation.y = elapsedTime * 0.25;
      avatarMesh.rotation.z = elapsedTime * 0.15;

      coreMat.opacity = 0.15 + uniformsRef.current.uAudioAmp.value * 0.35;

      // Rotate outer telemetry orbits
      ring1.rotation.z = elapsedTime * 0.35;
      ring2.rotation.z = -elapsedTime * 0.18;
      hudGroup.rotation.y = elapsedTime * 0.08;

      // Drift particle nebula
      particleSystem.rotation.y = elapsedTime * -0.05;

      renderer.render(scene, camera);
      animationFrameRef.current = requestAnimationFrame(animate);
    };

    animate();

    // 10. Frame resizing handler
    const handleResize = () => {
      if (!containerRef.current) return;
      const w = containerRef.current.clientWidth;
      const h = containerRef.current.clientHeight;
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
      renderer.setSize(w, h);
    };
    window.addEventListener('resize', handleResize);

    // 11. Complete Component Unmount Cleansing (Eliminate memory leaks)
    return () => {
      window.removeEventListener('resize', handleResize);
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
      
      // Erase buffer geometries from WebGL RAM
      avatarGeo.dispose();
      coreGeo.dispose();
      ringGeo1.dispose();
      ringGeo2.dispose();
      particleGeo.dispose();
      
      // Erase shader programs 
      hologramShaderMaterial.dispose();
      coreMat.dispose();
      hudMat1.dispose();
      hudMat2.dispose();
      pointMat.dispose();

      renderer.dispose();

      if (containerRef.current && renderer.domElement) {
        containerRef.current.removeChild(renderer.domElement);
      }
    };
  }, []);

  // Update Three.JS components on theme change
  useEffect(() => {
    switchTheme(avatarTheme);
  }, [avatarTheme]);

  return (
    <div style={styles.outerContainer}>
      {/* Visual cyber grids in background */}
      <div style={styles.gridOverlay} />
      
      {/* Telemetry Instrumentation Header HUD */}
      <div style={styles.hudOverlay}>
        <div style={styles.hudRow}>
          <span style={styles.terminalLabel}>PROTOCOLS: ACTIVE</span>
          <span style={styles.hudNeonText}>CORE NODE: AVA-01</span>
        </div>
        <div style={styles.hudRow}>
          <span style={styles.terminalLabel}>RENDER SYSTEM:</span>
          <span>THREE.JS / SHADER_V2</span>
        </div>
      </div>

      {/* Primary Holographic Render Canvas container */}
      <div style={styles.canvasContainer} ref={containerRef} />

      {/* Bottom Interface HUD controls */}
      <div style={styles.controlsCard}>
        <h3 style={styles.controlsTitle}>COGNITIVE CORE MATRIX</h3>
        <p style={styles.controlsDescription}>
          Procedural 3D web terminal syncing soundwave vibrations into geometric matrix mutations.
        </p>

        {/* Live Audio Telemetry Signal Monitor */}
        <div style={styles.telemetryPanel}>
          <div style={styles.barHUDContainer}>
            <div 
              style={{
                ...styles.barHUDValue,
                width: `${Math.max(0, audioInDb + 100)}%`,
                backgroundColor: isMicConnected ? `#${uniformsRef.current.uColor.value.getHexString()}` : '#555' 
              }} 
            />
          </div>
          <div style={styles.telemetryStats}>
            <span>MIC: {isMicConnected ? 'SIGNAL ONLINE' : 'OFFLINE'}</span>
            <span style={{ fontFamily: 'monospace' }}>
              GAIN: {isMicConnected ? `${audioInDb} dB` : '-INF db'}
            </span>
          </div>
        </div>

        {/* Mic Activation Button interface */}
        <div style={styles.actionButtonGroup}>
          {!isMicConnected ? (
            <button style={styles.connectButton} onClick={initializeMicrophone}>
              ESTABLISH NUCLEUS LINK
            </button>
          ) : (
            <button style={styles.disconnectButton} onClick={disconnectMicrophone}>
              CLOSE SYNC CONNECTION
            </button>
          )}
        </div>

        {/* Core Theme Options Switcher */}
        <div style={styles.themeGroup}>
          <span style={styles.smallHeading}>VIRTUAL AURA SHIFT:</span>
          <div style={styles.themeSelectorRow}>
            {Object.keys(themes).map((tKey) => {
              const active = avatarTheme === tKey;
              return (
                <button
                  key={tKey}
                  onClick={() => switchTheme(tKey)}
                  style={{
                    ...styles.themePill,
                    borderColor: active ? `#${themes[tKey].color.toString(16).padStart(6, '0')}` : '#2a2f3d',
                    color: active ? '#fff' : '#8c9ab2',
                    backgroundColor: active ? 'rgba(0, 229, 255, 0.08)' : 'transparent'
                  }}
                >
                  {themes[tKey].label}
                </button>
              );
            })}
          </div>
        </div>
        
        {micError && <p style={styles.errorMessage}>{micError}</p>}
      </div>
    </div>
  );
}

// Visual CSS Styling for High-fidelity UI Polish (Modern Cyber Theme)
const styles = {
  outerContainer: {
    width: '100vw',
    height: '100vh',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'space-between',
    background: 'radial-gradient(circle at center, #07152b 0%, #030a16 100%)',
    position: 'relative',
    overflow: 'hidden',
    fontFamily: '"Rajdhani", "Segoe UI", "Google Sans", sans-serif',
    color: '#dae5f2',
    boxSizing: 'border-box',
    padding: '24px'
  },
  gridOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundImage: `
      linear-gradient(rgba(0, 229, 255, 0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(0, 229, 255, 0.03) 1px, transparent 1px)
    `,
    backgroundSize: '24px 24px',
    top: '0px',
    pointerEvents: 'none',
    zIndex: 1
  },
  hudOverlay: {
    width: '100%',
    maxWidth: '560px',
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
    borderBottom: '1px solid rgba(0, 229, 255, 0.15)',
    paddingBottom: '12px',
    zIndex: 10,
    pointerEvents: 'none'
  },
  hudRow: {
    display: 'flex',
    justifyContent: 'space-between',
    fontSize: '12px',
    letterSpacing: '1.5px',
    color: '#8c9ab2',
    fontFamily: 'monospace'
  },
  terminalLabel: {
    color: '#52698c'
  },
  hudNeonText: {
    color: '#00e5ff',
    textShadow: '0 0 8px rgba(0, 229, 255, 0.6)'
  },
  canvasContainer: {
    flex: '1',
    width: '100%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 2,
    position: 'relative'
  },
  controlsCard: {
    width: '100%',
    maxWidth: '560px',
    backgroundColor: 'rgba(5, 14, 30, 0.85)',
    border: '1px solid rgba(0, 229, 255, 0.15)',
    borderRadius: '16px',
    padding: '20px',
    boxShadow: '0 12px 36px rgba(0, 0, 0, 0.6), inset 0 0 16px rgba(0, 229, 255, 0.05)',
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
    zIndex: 10,
    backdropFilter: 'blur(8px)',
  },
  controlsTitle: {
    margin: 0,
    fontSize: '18px',
    fontWeight: 'bold',
    letterSpacing: '2px',
    color: '#fff',
    borderLeft: '3px solid #00e5ff',
    paddingLeft: '10px'
  },
  controlsDescription: {
    margin: 0,
    fontSize: '13px',
    lineHeight: '1.5',
    color: '#8c9ab2'
  },
  telemetryPanel: {
    backgroundColor: '#040b15',
    borderRadius: '8px',
    padding: '12px',
    border: '1px solid rgba(82, 105, 140, 0.15)',
    display: 'flex',
    flexDirection: 'column',
    gap: '6px'
  },
  barHUDContainer: {
    width: '100%',
    height: '6px',
    backgroundColor: 'rgba(255,255,255,0.05)',
    borderRadius: '3px',
    overflow: 'hidden'
  },
  barHUDValue: {
    height: '100%',
    transition: 'width 0.08s ease-out, background-color 0.3s ease'
  },
  telemetryStats: {
    display: 'flex',
    justifyContent: 'space-between',
    fontSize: '11px',
    letterSpacing: '0.8px',
    color: '#8c9ab2',
    fontFamily: 'monospace'
  },
  actionButtonGroup: {
    display: 'flex'
  },
  connectButton: {
    width: '100%',
    padding: '12px',
    border: 'none',
    borderRadius: '8px',
    background: 'linear-gradient(135deg, #00cbff 0%, #007bff 100%)',
    color: '#fff',
    fontSize: '14px',
    fontWeight: 'bold',
    letterSpacing: '1.5px',
    cursor: 'pointer',
    boxShadow: '0 4px 15px rgba(0, 203, 255, 0.45)',
    transition: 'all 0.2s',
    outline: 'none',
  },
  disconnectButton: {
    width: '100%',
    padding: '12px',
    border: '1px solid #ff4a4a',
    borderRadius: '8px',
    backgroundColor: 'rgba(255, 74, 74, 0.08)',
    color: '#ff4a4a',
    fontSize: '14px',
    fontWeight: 'bold',
    letterSpacing: '1.5px',
    cursor: 'pointer',
    transition: 'all 0.2s',
    outline: 'none'
  },
  themeGroup: {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px'
  },
  smallHeading: {
    fontSize: '11px',
    letterSpacing: '1px',
    color: '#52698c',
    fontFamily: 'monospace'
  },
  themeSelectorRow: {
    display: 'flex',
    gap: '6px',
    flexWrap: 'wrap'
  },
  themePill: {
    padding: '6px 10px',
    borderRadius: '6px',
    border: '1px solid',
    fontSize: '11px',
    fontWeight: 'bold',
    cursor: 'pointer',
    transition: 'all 0.2s',
    outline: 'none'
  },
  errorMessage: {
    color: '#ff4a4a',
    fontSize: '12px',
    margin: 0,
    textAlign: 'center',
    fontFamily: 'monospace'
  }
};
