"""
Vercel-compatible Music Classification Service (Minimal Version)

This Flask service provides Christian/Secular music classification using
the trained model from the christian_music_classifier directory.
Optimized for Vercel serverless deployment with minimal dependencies.
"""

import os
import sys
import json
import logging
import traceback
import time
from pathlib import Path
from typing import Dict, List, Any, Optional
import numpy as np
import joblib
from flask import Flask, request, jsonify
from flask_cors import CORS

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize Flask app
app = Flask(__name__)
CORS(app)  # Enable CORS for Android app requests

# Global variables for model and feature extractor
model_data = None

# Performance monitoring
request_count = 0
start_time = time.time()

class MinimalAudioFeatureExtractor:
    """Minimal audio feature extractor using only numpy (no librosa)."""
    
    def __init__(self, sample_rate: int = 22050, duration: int = 10):
        """Initialize feature extractor with minimal dependencies."""
        self.sample_rate = sample_rate
        self.duration = duration
        self.target_length = sample_rate * duration
        self.epsilon = 1e-8
        
    def extract_features_from_array(self, audio_data: np.ndarray, sample_rate: int) -> Optional[Dict[str, float]]:
        """
        Extract basic features using only numpy operations.
        """
        try:
            # Simple resampling if necessary (basic linear interpolation)
            if sample_rate != self.sample_rate:
                y = self._simple_resample(audio_data, sample_rate, self.sample_rate)
            else:
                y = audio_data
            
            # Truncate or pad to desired duration
            if len(y) > self.target_length:
                y = y[:self.target_length]
            elif len(y) < self.target_length:
                y = np.pad(y, (0, self.target_length - len(y)), mode='constant')
            
            if len(y) == 0:
                return None
            
            features = {}
            
            # Basic audio properties
            y_abs = np.abs(y)
            y_squared = y ** 2
            rms = np.sqrt(np.mean(y_squared))
            max_abs = np.max(y_abs)
            
            features['signal_length_ratio'] = float(len(y) / self.target_length)
            features['rms_energy_ratio'] = float(rms / (max_abs + self.epsilon))
            
            # Basic spectral features using FFT
            fft = np.fft.fft(y)
            magnitude = np.abs(fft[:len(fft)//2])
            freqs = np.fft.fftfreq(len(y), 1/sample_rate)[:len(fft)//2]
            
            # Spectral centroid (simplified)
            if np.sum(magnitude) > 0:
                spectral_centroid = np.sum(freqs * magnitude) / np.sum(magnitude)
            else:
                spectral_centroid = 0.0
            
            features['spectral_centroid_mean'] = float(spectral_centroid)
            features['spectral_centroid_std'] = float(np.std(magnitude))
            features['spectral_centroid_skew'] = float(self._skewness(magnitude))
            
            # Spectral rolloff (simplified)
            cumsum = np.cumsum(magnitude)
            rolloff_idx = np.where(cumsum >= 0.85 * cumsum[-1])[0]
            if len(rolloff_idx) > 0:
                spectral_rolloff = freqs[rolloff_idx[0]]
            else:
                spectral_rolloff = freqs[-1]
            
            features['spectral_rolloff_mean'] = float(spectral_rolloff)
            features['spectral_rolloff_std'] = float(np.std(magnitude))
            
            # Spectral bandwidth (simplified)
            spectral_bandwidth = np.sqrt(np.sum(((freqs - spectral_centroid) ** 2) * magnitude) / np.sum(magnitude))
            features['spectral_bandwidth_mean'] = float(spectral_bandwidth)
            features['spectral_bandwidth_std'] = float(np.std(magnitude))
            
            # Zero crossing rate
            zero_crossings = np.sum(np.diff(np.sign(y)) != 0)
            zcr = zero_crossings / len(y)
            features['zcr_mean'] = float(zcr)
            features['zcr_std'] = float(np.std(np.diff(np.sign(y))))
            
            # Basic MFCC-like features using DCT
            mfcc_like = self._simple_mfcc(y)
            for i in range(min(13, len(mfcc_like))):
                features[f'mfcc_{i+1}_mean'] = float(np.mean(mfcc_like[i]))
                features[f'mfcc_{i+1}_std'] = float(np.std(mfcc_like[i]))
            
            # Fill remaining MFCC features with zeros
            for i in range(len(mfcc_like), 13):
                features[f'mfcc_{i+1}_mean'] = 0.0
                features[f'mfcc_{i+1}_std'] = 0.0
            
            # Basic chroma-like features
            chroma_like = self._simple_chroma(magnitude, freqs)
            features['chroma_mean'] = float(np.mean(chroma_like))
            features['chroma_std'] = float(np.std(chroma_like))
            
            for i in range(12):
                features[f'chroma_bin_{i}'] = float(chroma_like[i])
            
            # Basic tonnetz-like features
            tonnetz_like = self._simple_tonnetz(chroma_like)
            features['tonnetz_mean'] = float(np.mean(tonnetz_like))
            features['tonnetz_std'] = float(np.std(tonnetz_like))
            
            # Tempo estimation (simplified)
            tempo = self._simple_tempo(y)
            features['tempo'] = float(tempo)
            features['beat_strength'] = float(np.std(y))
            
            # Spectral contrast (simplified)
            contrast = self._simple_spectral_contrast(magnitude)
            features['spectral_contrast_mean'] = float(np.mean(contrast))
            features['spectral_contrast_std'] = float(np.std(contrast))
            
            # Spectral flatness (simplified)
            flatness = self._simple_spectral_flatness(magnitude)
            features['spectral_flatness_mean'] = float(np.mean(flatness))
            features['spectral_flatness_std'] = float(np.std(flatness))
            
            # Dynamic features
            features['dynamic_range'] = float(np.percentile(y_abs, 95) - np.percentile(y_abs, 5))
            features['peak_to_rms_ratio'] = float(max_abs / (rms + self.epsilon))
            
            # Harmonic-percussive separation (simplified)
            harmonic_ratio, percussive_ratio = self._simple_hpss(y)
            features['harmonic_ratio'] = float(harmonic_ratio)
            features['percussive_ratio'] = float(percussive_ratio)
            
            # Additional features
            features['spectral_centroid_normalized'] = float(spectral_centroid / (sample_rate / 2))
            features['silence_ratio'] = float(np.sum(y_abs < 0.01) / len(y))
            
            return features
            
        except Exception as e:
            logger.error(f"Error extracting features: {e}")
            return None
    
    def _simple_resample(self, signal, orig_sr, target_sr):
        """Simple linear interpolation resampling."""
        if orig_sr == target_sr:
            return signal
        
        ratio = target_sr / orig_sr
        new_length = int(len(signal) * ratio)
        
        # Simple linear interpolation
        old_indices = np.linspace(0, len(signal) - 1, len(signal))
        new_indices = np.linspace(0, len(signal) - 1, new_length)
        
        return np.interp(new_indices, old_indices, signal)
    
    def _skewness(self, data):
        """Calculate skewness."""
        mean = np.mean(data)
        std = np.std(data)
        if std == 0:
            return 0.0
        return np.mean(((data - mean) / std) ** 3)
    
    def _simple_mfcc(self, signal):
        """Simplified MFCC using DCT."""
        # Apply window
        windowed = signal * np.hanning(len(signal))
        
        # FFT
        fft = np.fft.fft(windowed)
        magnitude = np.abs(fft[:len(fft)//2])
        
        # Mel-scale filter bank (simplified)
        mel_filters = self._create_mel_filters(len(magnitude), 26)
        mel_spectrum = np.dot(mel_filters, magnitude)
        
        # Log
        log_mel = np.log(mel_spectrum + self.epsilon)
        
        # DCT
        mfcc = np.fft.dct(log_mel, type=2, norm='ortho')
        
        return mfcc[:13]  # Return first 13 coefficients
    
    def _create_mel_filters(self, n_fft, n_mels):
        """Create simplified mel-scale filter bank."""
        filters = np.zeros((n_mels, n_fft))
        
        # Simplified mel scale
        mel_points = np.linspace(0, n_fft, n_mels + 2)
        
        for i in range(n_mels):
            left = int(mel_points[i])
            center = int(mel_points[i + 1])
            right = int(mel_points[i + 2])
            
            # Rising edge
            filters[i, left:center] = np.linspace(0, 1, center - left)
            # Falling edge
            filters[i, center:right] = np.linspace(1, 0, right - center)
        
        return filters
    
    def _simple_chroma(self, magnitude, freqs):
        """Simplified chroma features."""
        # Map frequencies to chroma bins
        chroma = np.zeros(12)
        
        for i, freq in enumerate(freqs):
            if freq > 0:
                # Convert frequency to chroma bin
                chroma_bin = int(12 * np.log2(freq / 440.0)) % 12
                chroma[chroma_bin] += magnitude[i]
        
        # Normalize
        if np.sum(chroma) > 0:
            chroma = chroma / np.sum(chroma)
        
        return chroma
    
    def _simple_tonnetz(self, chroma):
        """Simplified tonnetz features."""
        # Basic tonnetz calculation
        tonnetz = np.zeros(6)
        
        # Simplified tonnetz mapping
        tonnetz[0] = chroma[0] - chroma[3]  # C - Eb
        tonnetz[1] = chroma[1] - chroma[4]  # C# - E
        tonnetz[2] = chroma[2] - chroma[5]  # D - F
        tonnetz[3] = chroma[3] - chroma[6]  # Eb - F#
        tonnetz[4] = chroma[4] - chroma[7]  # E - G
        tonnetz[5] = chroma[5] - chroma[8]  # F - G#
        
        return tonnetz
    
    def _simple_tempo(self, signal):
        """Simplified tempo estimation."""
        # Basic tempo estimation using autocorrelation
        autocorr = np.correlate(signal, signal, mode='full')
        autocorr = autocorr[autocorr.size // 2:]
        
        # Find peaks
        peaks = []
        for i in range(1, len(autocorr) - 1):
            if autocorr[i] > autocorr[i-1] and autocorr[i] > autocorr[i+1]:
                peaks.append(i)
        
        if len(peaks) > 1:
            # Estimate tempo from peak intervals
            intervals = np.diff(peaks)
            if len(intervals) > 0:
                avg_interval = np.mean(intervals)
                tempo = 60.0 * self.sample_rate / avg_interval
                return min(max(tempo, 60), 200)  # Clamp between 60-200 BPM
        
        return 120.0  # Default tempo
    
    def _simple_spectral_contrast(self, magnitude):
        """Simplified spectral contrast."""
        # Divide spectrum into bands
        n_bands = 7
        band_size = len(magnitude) // n_bands
        
        contrast = []
        for i in range(n_bands):
            start = i * band_size
            end = (i + 1) * band_size
            band = magnitude[start:end]
            
            if len(band) > 0:
                # Contrast as difference between max and mean
                contrast.append(np.max(band) - np.mean(band))
            else:
                contrast.append(0.0)
        
        return np.array(contrast)
    
    def _simple_spectral_flatness(self, magnitude):
        """Simplified spectral flatness."""
        # Geometric mean / arithmetic mean
        geometric_mean = np.exp(np.mean(np.log(magnitude + self.epsilon)))
        arithmetic_mean = np.mean(magnitude)
        
        if arithmetic_mean > 0:
            return geometric_mean / arithmetic_mean
        else:
            return 0.0
    
    def _simple_hpss(self, signal):
        """Simplified harmonic-percussive separation."""
        # Very basic separation using median filtering
        harmonic = np.zeros_like(signal)
        percussive = np.zeros_like(signal)
        
        # Simple median filter for harmonic
        window_size = min(31, len(signal) // 4)
        if window_size > 1:
            for i in range(len(signal)):
                start = max(0, i - window_size // 2)
                end = min(len(signal), i + window_size // 2 + 1)
                harmonic[i] = np.median(signal[start:end])
        
        percussive = signal - harmonic
        
        # Calculate ratios
        harmonic_energy = np.sum(harmonic ** 2)
        percussive_energy = np.sum(percussive ** 2)
        total_energy = harmonic_energy + percussive_energy
        
        if total_energy > 0:
            harmonic_ratio = harmonic_energy / total_energy
            percussive_ratio = percussive_energy / total_energy
        else:
            harmonic_ratio = 0.5
            percussive_ratio = 0.5
        
        return harmonic_ratio, percussive_ratio

def load_model():
    """Load the trained model from the christian_music_classifier directory."""
    global model_data
    
    try:
        # Try multiple possible paths for the model
        model_paths = [
            "models/improved_audio_classifier_random_forest.joblib",
            "../christian_music_classifier/models/improved_audio_classifier_random_forest.joblib",
            "/opt/render/project/src/christian_music_classifier/models/improved_audio_classifier_random_forest.joblib",
            "/var/task/models/improved_audio_classifier_random_forest.joblib",  # Vercel path
            "./models/improved_audio_classifier_random_forest.joblib"
        ]
        
        model_path = None
        for path in model_paths:
            if os.path.exists(path):
                model_path = path
                break
        
        if model_path is None:
            raise FileNotFoundError("Trained model not found in any expected location")
        
        logger.info(f"Loading model from: {model_path}")
        model_data = joblib.load(model_path)
        
        logger.info("✅ Model loaded successfully!")
        logger.info(f"   Model type: {model_data['model_type']}")
        logger.info(f"   Features: {len(model_data['feature_names'])} → {len(model_data['selected_feature_names'])} (selected)")
        logger.info(f"   Label map: {model_data['label_map']}")
        
        return True
        
    except Exception as e:
        logger.error(f"❌ Failed to load model: {e}")
        logger.error(traceback.format_exc())
        return False

def classify_features(features: Dict[str, float]) -> Dict[str, Any]:
    """
    Classify audio features using the loaded model.
    
    Args:
        features: Dictionary of audio features
        
    Returns:
        Classification result with prediction and confidence
    """
    try:
        if model_data is None:
            raise ValueError("Model not loaded")
        
        # Prepare features array in the same order as training
        feature_names = model_data['feature_names']
        X = np.array([[features.get(name, 0.0) for name in feature_names]])
        
        # Apply the same preprocessing pipeline used during training
        X_variance_filtered = model_data['variance_selector'].transform(X)
        X_scaled = model_data['scaler'].transform(X_variance_filtered)
        X_processed = model_data['feature_selector'].transform(X_scaled)
        
        # Make prediction
        pred_numeric = model_data['model'].predict(X_processed)[0]
        pred_proba = model_data['model'].predict_proba(X_processed)[0]
        
        # Convert to label
        pred_label = model_data['label_map'][pred_numeric]
        confidence = float(max(pred_proba))
        
        # Get individual class probabilities
        christian_prob = float(pred_proba[0])  # Christian = 0
        secular_prob = float(pred_proba[1])    # Secular = 1
        
        return {
            'prediction': pred_label,
            'confidence': confidence,
            'probabilities': {
                'christian': christian_prob,
                'secular': secular_prob
            },
            'success': True
        }
        
    except Exception as e:
        logger.error(f"Classification error: {e}")
        return {
            'prediction': 'unknown',
            'confidence': 0.0,
            'probabilities': {
                'christian': 0.5,
                'secular': 0.5
            },
            'success': False,
            'error': str(e)
        }

# Load model on startup
if not load_model():
    logger.error("Failed to load model. Service may not work correctly.")

@app.route('/', methods=['GET'])
def root():
    """Root endpoint with service information."""
    return jsonify({
        'service': 'Music Classification Service (Vercel Minimal)',
        'version': '1.0.0',
        'platform': 'Vercel Serverless',
        'endpoints': {
            'health': '/health',
            'classify': '/classify',
            'model_info': '/model_info'
        },
        'status': 'running',
        'note': 'Minimal version without librosa for Vercel compatibility'
    })

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint."""
    return jsonify({
        'status': 'healthy',
        'service': 'music-classification-service',
        'version': '1.0.0',
        'model_loaded': model_data is not None,
        'platform': 'Vercel Minimal'
    })

@app.route('/classify', methods=['POST'])
def classify_single():
    """
    Classify a single song's audio features.
    
    Expected JSON payload:
    {
        "audio_data": [list of audio samples],
        "sample_rate": 22050,
        "song_id": "optional_song_identifier"
    }
    """
    global request_count
    request_count += 1
    
    try:
        data = request.get_json()
        
        if not data:
            raise BadRequest("No JSON data provided")
        
        audio_data = data.get('audio_data')
        sample_rate = data.get('sample_rate', 22050)
        song_id = data.get('song_id', 'unknown')
        
        if audio_data is None:
            raise BadRequest("audio_data field is required")
        
        if not isinstance(audio_data, list):
            raise BadRequest("audio_data must be a list of numbers")
        
        # Convert to numpy array
        audio_array = np.array(audio_data, dtype=np.float32)
        
        if len(audio_array) == 0:
            raise BadRequest("audio_data cannot be empty")
        
        logger.info(f"Classifying song {song_id} with {len(audio_array)} samples")
        
        # Extract features using minimal extractor
        feature_extractor = MinimalAudioFeatureExtractor()
        features = feature_extractor.extract_features_from_array(audio_array, sample_rate)
        
        if features is None:
            raise BadRequest("Failed to extract features from audio data")
        
        # Classify
        result = classify_features(features)
        result['song_id'] = song_id
        
        logger.info(f"Classification result for {song_id}: {result['prediction']} (confidence: {result['confidence']:.3f})")
        
        return jsonify(result)
        
    except BadRequest as e:
        return jsonify({'error': str(e)}), 400
    except Exception as e:
        logger.error(f"Error in classify_single: {e}")
        logger.error(traceback.format_exc())
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/model_info', methods=['GET'])
def model_info():
    """Get information about the loaded model."""
    if model_data is None:
        return jsonify({'error': 'Model not loaded'}), 503
    
    # Convert numpy arrays to lists for JSON serialization
    class_weights = model_data.get('class_weights', {})
    if isinstance(class_weights, dict):
        # Convert numpy arrays in class_weights to lists
        serializable_class_weights = {}
        for key, value in class_weights.items():
            if hasattr(value, 'tolist'):  # numpy array
                serializable_class_weights[key] = value.tolist()
            else:
                serializable_class_weights[key] = value
    else:
        serializable_class_weights = {}
    
    return jsonify({
        'model_type': model_data['model_type'],
        'total_features': len(model_data['feature_names']),
        'selected_features': len(model_data['selected_feature_names']),
        'label_map': model_data['label_map'],
        'class_weights': serializable_class_weights,
        'feature_names': model_data['feature_names'],
        'selected_feature_names': model_data['selected_feature_names'],
        'platform': 'Vercel Minimal',
        'note': 'Minimal version without librosa for size optimization'
    })

@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint not found'}), 404

@app.errorhandler(500)
def internal_error(error):
    return jsonify({'error': 'Internal server error'}), 500

# Vercel entry point
def handler(request):
    """Vercel serverless function entry point."""
    return app(request.environ, lambda *args: None)

if __name__ == '__main__':
    logger.info("🚀 Starting Vercel Minimal Music Classification Service...")
    
    # Load model
    if load_model():
        logger.info("✅ Service ready!")
        logger.info("📡 Available endpoints:")
        logger.info("   GET  /health - Health check")
        logger.info("   POST /classify - Classify single song")
        logger.info("   GET  /model_info - Get model information")
        logger.info("   GET  / - Service information")
        logger.info("🔧 Optimization: Minimal dependencies for Vercel")
        logger.info("💾 Size optimized for Vercel (no librosa)")
        
        # Run Flask app
        app.run(host='0.0.0.0', port=int(os.environ.get('PORT', 5000)), debug=False)
    else:
        logger.error("❌ Failed to start service - model loading failed")
        sys.exit(1)
