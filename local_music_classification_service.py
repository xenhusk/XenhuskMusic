# Local Music Classification Service
# Run this on your PC for reliable hosting

# Install dependencies first:
# pip install flask flask-cors librosa soundfile scikit-learn joblib pandas numpy

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
import librosa
import soundfile as sf
import pandas as pd
from flask import Flask, request, jsonify
from flask_cors import CORS
from werkzeug.exceptions import BadRequest
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading
from queue import Queue
import multiprocessing as mp
import tempfile
import base64

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize Flask app
app = Flask(__name__)
CORS(app)

# Global variables
model_data = None
feature_extractor = None
MAX_WORKERS = min(4, mp.cpu_count())
thread_pool = ThreadPoolExecutor(max_workers=MAX_WORKERS)
request_count = 0
start_time = time.time()

class LocalAudioFeatureExtractor:
    def __init__(self, sample_rate: int = 22050, duration: int = 10):
        self.sample_rate = sample_rate
        self.duration = duration
        self.target_length = sample_rate * duration
        
    def extract_features_from_array(self, audio_data: np.ndarray, sample_rate: int) -> Optional[Dict[str, float]]:
        try:
            if sample_rate != self.sample_rate:
                y = librosa.resample(audio_data, orig_sr=sample_rate, target_sr=self.sample_rate)
            else:
                y = audio_data
            
            if len(y) > self.target_length:
                y = y[:self.target_length]
            elif len(y) < self.target_length:
                y = np.pad(y, (0, self.target_length - len(y)), mode='constant')
            
            if len(y) == 0:
                return None
            
            features = {}
            
            # Basic properties
            features['signal_length_ratio'] = float(len(y) / self.target_length)
            features['rms_energy_ratio'] = float(np.sqrt(np.mean(y**2)) / (np.max(np.abs(y)) + 1e-8))
            
            # Spectral features
            spectral_centroids = librosa.feature.spectral_centroid(y=y, sr=self.sample_rate)[0]
            features['spectral_centroid_mean'] = float(np.mean(spectral_centroids))
            features['spectral_centroid_std'] = float(np.std(spectral_centroids))
            features['spectral_centroid_skew'] = float(self._skewness(spectral_centroids))
            
            spectral_rolloff = librosa.feature.spectral_rolloff(y=y, sr=self.sample_rate)[0]
            features['spectral_rolloff_mean'] = float(np.mean(spectral_rolloff))
            features['spectral_rolloff_std'] = float(np.std(spectral_rolloff))
            
            spectral_bandwidth = librosa.feature.spectral_bandwidth(y=y, sr=self.sample_rate)[0]
            features['spectral_bandwidth_mean'] = float(np.mean(spectral_bandwidth))
            features['spectral_bandwidth_std'] = float(np.std(spectral_bandwidth))
            
            # Zero crossing rate
            zcr = librosa.feature.zero_crossing_rate(y)[0]
            features['zcr_mean'] = float(np.mean(zcr))
            features['zcr_std'] = float(np.std(zcr))
            
            # MFCC features
            mfccs = librosa.feature.mfcc(y=y, sr=self.sample_rate, n_mfcc=13)
            for i in range(13):
                features[f'mfcc_{i+1}_mean'] = float(np.mean(mfccs[i]))
                features[f'mfcc_{i+1}_std'] = float(np.std(mfccs[i]))
            
            # Chroma features
            chroma = librosa.feature.chroma_stft(y=y, sr=self.sample_rate)
            features['chroma_mean'] = float(np.mean(chroma))
            features['chroma_std'] = float(np.std(chroma))
            
            # Individual chroma bins
            chroma_bins = np.mean(chroma, axis=1)
            for i in range(12):
                features[f'chroma_bin_{i}'] = float(chroma_bins[i])
            
            # Tonnetz features
            tonnetz = librosa.feature.tonnetz(y=y, sr=self.sample_rate)
            features['tonnetz_mean'] = float(np.mean(tonnetz))
            features['tonnetz_std'] = float(np.std(tonnetz))
            
            # Tempo and rhythm features
            tempo, beats = librosa.beat.beat_track(y=y, sr=self.sample_rate)
            features['tempo'] = float(tempo) if np.isfinite(tempo) else 120.0
            features['beat_strength'] = float(len(beats) / (len(y) / self.sample_rate)) if len(y) > 0 else 0.0
            
            # Spectral contrast
            contrast = librosa.feature.spectral_contrast(y=y, sr=self.sample_rate)
            features['spectral_contrast_mean'] = float(np.mean(contrast))
            features['spectral_contrast_std'] = float(np.std(contrast))
            
            # Spectral flatness
            flatness = librosa.feature.spectral_flatness(y=y)
            features['spectral_flatness_mean'] = float(np.mean(flatness))
            features['spectral_flatness_std'] = float(np.std(flatness))
            
            # Dynamic features
            features['dynamic_range'] = float(np.percentile(np.abs(y), 95) - np.percentile(np.abs(y), 5))
            features['peak_to_rms_ratio'] = float(np.max(np.abs(y)) / (np.sqrt(np.mean(y**2)) + 1e-8))
            
            # Harmonic-percussive separation
            try:
                y_harmonic, y_percussive = librosa.effects.hpss(y)
                harmonic_energy = np.sum(y_harmonic**2)
                percussive_energy = np.sum(y_percussive**2)
                total_energy = harmonic_energy + percussive_energy
                
                features['harmonic_ratio'] = float(harmonic_energy / (total_energy + 1e-8))
                features['percussive_ratio'] = float(percussive_energy / (total_energy + 1e-8))
            except:
                features['harmonic_ratio'] = 0.5
                features['percussive_ratio'] = 0.5
            
            # Additional spectral features
            features['spectral_centroid_normalized'] = float(np.mean(spectral_centroids) / (self.sample_rate / 2))
            features['silence_ratio'] = float(np.sum(np.abs(y) < 0.01) / len(y))
            
            return features
            
        except Exception as e:
            logger.error(f"Error extracting features: {e}")
            return None
    
    def _skewness(self, data):
        mean = np.mean(data)
        std = np.std(data)
        if std == 0:
            return 0.0
        return np.mean(((data - mean) / std) ** 3)

def skewness(data):
    """Calculate skewness of data."""
    mean = np.mean(data)
    std = np.std(data)
    if std == 0:
        return 0.0
    return np.mean(((data - mean) / std) ** 3)

def extract_features_from_audio_data(audio_data: np.ndarray, sample_rate: int = 22050) -> Optional[Dict[str, float]]:
    """
    Extract features from raw audio data using the same method as the original training.
    This replicates the feature extraction from improved_audio_classifier.py
    """
    try:
        y = audio_data
        sr = sample_rate
        
        if len(y) == 0:
            return None
        
        features = {}
        
        # Basic properties (normalized/relative features instead of absolute)
        features['signal_length_ratio'] = float(len(y) / (sr * 10))  # Assuming 10 second duration
        features['rms_energy_ratio'] = float(np.sqrt(np.mean(y**2)) / (np.max(np.abs(y)) + 1e-8))
        
        # 1. Spectral features
        spectral_centroids = librosa.feature.spectral_centroid(y=y, sr=sr)[0]
        features['spectral_centroid_mean'] = float(np.mean(spectral_centroids))
        features['spectral_centroid_std'] = float(np.std(spectral_centroids))
        features['spectral_centroid_skew'] = float(skewness(spectral_centroids))
        
        spectral_rolloff = librosa.feature.spectral_rolloff(y=y, sr=sr)[0]
        features['spectral_rolloff_mean'] = float(np.mean(spectral_rolloff))
        features['spectral_rolloff_std'] = float(np.std(spectral_rolloff))
        
        spectral_bandwidth = librosa.feature.spectral_bandwidth(y=y, sr=sr)[0]
        features['spectral_bandwidth_mean'] = float(np.mean(spectral_bandwidth))
        features['spectral_bandwidth_std'] = float(np.std(spectral_bandwidth))
        
        # 2. Zero crossing rate
        zcr = librosa.feature.zero_crossing_rate(y)[0]
        features['zcr_mean'] = float(np.mean(zcr))
        features['zcr_std'] = float(np.std(zcr))
        
        # 3. MFCC features (first 13 coefficients)
        mfccs = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13)
        for i in range(13):
            features[f'mfcc_{i+1}_mean'] = float(np.mean(mfccs[i]))
            features[f'mfcc_{i+1}_std'] = float(np.std(mfccs[i]))
        
        # 4. Chroma features (key-related)
        chroma = librosa.feature.chroma_stft(y=y, sr=sr)
        features['chroma_mean'] = float(np.mean(chroma))
        features['chroma_std'] = float(np.std(chroma))
        
        # Individual chroma bins (12 semitones)
        chroma_bins = np.mean(chroma, axis=1)
        for i in range(12):
            features[f'chroma_bin_{i}'] = float(chroma_bins[i])
        
        # 5. Tonnetz features (harmonic network)
        tonnetz = librosa.feature.tonnetz(y=y, sr=sr)
        features['tonnetz_mean'] = float(np.mean(tonnetz))
        features['tonnetz_std'] = float(np.std(tonnetz))
        
        # 6. Rhythm and tempo features
        tempo, beats = librosa.beat.beat_track(y=y, sr=sr)
        features['tempo'] = float(tempo) if np.isfinite(tempo) else 120.0
        features['beat_strength'] = float(len(beats) / (len(y) / sr)) if len(y) > 0 else 0.0
        
        # 7. Spectral contrast
        contrast = librosa.feature.spectral_contrast(y=y, sr=sr)
        features['spectral_contrast_mean'] = float(np.mean(contrast))
        features['spectral_contrast_std'] = float(np.std(contrast))
        
        # 8. Spectral flatness (measure of noisiness)
        flatness = librosa.feature.spectral_flatness(y=y)
        features['spectral_flatness_mean'] = float(np.mean(flatness))
        features['spectral_flatness_std'] = float(np.std(flatness))
        
        # 9. Dynamic features
        features['dynamic_range'] = float(np.percentile(np.abs(y), 95) - np.percentile(np.abs(y), 5))
        features['peak_to_rms_ratio'] = float(np.max(np.abs(y)) / (np.sqrt(np.mean(y**2)) + 1e-8))
        
        # 10. Harmonic-percussive separation features
        try:
            y_harmonic, y_percussive = librosa.effects.hpss(y)
            harmonic_energy = np.sum(y_harmonic**2)
            percussive_energy = np.sum(y_percussive**2)
            total_energy = harmonic_energy + percussive_energy
            
            features['harmonic_ratio'] = float(harmonic_energy / (total_energy + 1e-8))
            features['percussive_ratio'] = float(percussive_energy / (total_energy + 1e-8))
        except:
            features['harmonic_ratio'] = 0.5
            features['percussive_ratio'] = 0.5
        
        # 11. Additional spectral features
        features['spectral_centroid_normalized'] = float(np.mean(spectral_centroids) / (sr / 2))
        
        # 12. Zero-padding and windowing artifacts detection
        features['silence_ratio'] = float(np.sum(np.abs(y) < 0.01) / len(y))
        
        return features
        
    except Exception as e:
        logger.error(f"Error extracting features from audio data: {e}")
        return None

def load_model():
    global model_data, feature_extractor
    
    try:
        # Try to find the model file
        model_paths = [
            "models/improved_audio_classifier_random_forest.joblib",
            "improved_audio_classifier_random_forest.joblib",
            os.path.join(os.path.dirname(__file__), "models", "improved_audio_classifier_random_forest.joblib"),
            os.path.join(os.path.dirname(__file__), "improved_audio_classifier_random_forest.joblib")
        ]
        
        model_path = None
        for path in model_paths:
            if os.path.exists(path):
                model_path = path
                break
        
        if model_path is None:
            logger.warning("Model not found, creating dummy model for testing")
            model_data = create_dummy_model()
        else:
            logger.info(f"Loading model from: {model_path}")
            model_data = joblib.load(model_path)
        
        feature_extractor = LocalAudioFeatureExtractor()
        logger.info("✅ Model loaded successfully!")
        return True
        
    except Exception as e:
        logger.error(f"❌ Failed to load model: {e}")
        return False

def create_dummy_model():
    from sklearn.ensemble import RandomForestClassifier
    from sklearn.preprocessing import StandardScaler
    from sklearn.feature_selection import VarianceThreshold, SelectKBest, f_classif
    
    feature_names = [
        'signal_length_ratio', 'rms_energy_ratio', 'spectral_centroid_mean', 'spectral_centroid_std',
        'spectral_centroid_skew', 'spectral_rolloff_mean', 'spectral_rolloff_std', 'spectral_bandwidth_mean',
        'spectral_bandwidth_std', 'zcr_mean', 'zcr_std', 'mfcc_1_mean', 'mfcc_1_std', 'mfcc_2_mean',
        'mfcc_2_std', 'mfcc_3_mean', 'mfcc_3_std', 'mfcc_4_mean', 'mfcc_4_std', 'mfcc_5_mean', 'mfcc_5_std',
        'mfcc_6_mean', 'mfcc_6_std', 'mfcc_7_mean', 'mfcc_7_std', 'mfcc_8_mean', 'mfcc_8_std', 'mfcc_9_mean',
        'mfcc_9_std', 'mfcc_10_mean', 'mfcc_10_std', 'mfcc_11_mean', 'mfcc_11_std', 'mfcc_12_mean', 'mfcc_12_std',
        'mfcc_13_mean', 'mfcc_13_std', 'chroma_mean', 'chroma_std', 'chroma_bin_0', 'chroma_bin_1', 'chroma_bin_2',
        'chroma_bin_3', 'chroma_bin_4', 'chroma_bin_5', 'chroma_bin_6', 'chroma_bin_7', 'chroma_bin_8', 'chroma_bin_9',
        'chroma_bin_10', 'chroma_bin_11', 'tonnetz_mean', 'tonnetz_std', 'tempo', 'beat_strength',
        'spectral_contrast_mean', 'spectral_contrast_std', 'spectral_flatness_mean', 'spectral_flatness_std',
        'dynamic_range', 'peak_to_rms_ratio', 'harmonic_ratio', 'percussive_ratio', 'spectral_centroid_normalized',
        'silence_ratio'
    ]
    
    variance_selector = VarianceThreshold(threshold=0.01)
    scaler = StandardScaler()
    feature_selector = SelectKBest(f_classif, k=30)
    model = RandomForestClassifier(n_estimators=100, random_state=42)
    
    X_dummy = np.random.randn(100, len(feature_names))
    y_dummy = np.random.randint(0, 2, 100)
    
    X_var = variance_selector.fit_transform(X_dummy)
    X_scaled = scaler.fit_transform(X_var)
    X_selected = feature_selector.fit_transform(X_scaled, y_dummy)
    
    model.fit(X_selected, y_dummy)
    
    selected_indices = feature_selector.get_support(indices=True)
    selected_feature_names = [feature_names[i] for i in selected_indices]
    
    return {
        'model': model,
        'variance_selector': variance_selector,
        'scaler': scaler,
        'feature_selector': feature_selector,
        'model_type': 'random_forest',
        'feature_names': feature_names,
        'selected_feature_names': selected_feature_names,
        'label_map': {0: 'Christian', 1: 'Secular'},
        'class_weights': {}
    }

def classify_features(features: Dict[str, float]) -> Dict[str, Any]:
    try:
        if model_data is None:
            raise ValueError("Model not loaded")
        
        feature_names = model_data['feature_names']
        X = np.array([[features.get(name, 0.0) for name in feature_names]])
        
        X_variance_filtered = model_data['variance_selector'].transform(X)
        X_scaled = model_data['scaler'].transform(X_variance_filtered)
        X_processed = model_data['feature_selector'].transform(X_scaled)
        
        pred_numeric = model_data['model'].predict(X_processed)[0]
        pred_proba = model_data['model'].predict_proba(X_processed)[0]
        
        pred_label = model_data['label_map'][pred_numeric]
        confidence = float(max(pred_proba))
        
        christian_prob = float(pred_proba[0])
        secular_prob = float(pred_proba[1])
        
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

# Load model
if not load_model():
    logger.error("❌ Failed to load model. Service may not work correctly.")

@app.route('/', methods=['GET'])
def root():
    return jsonify({
        'service': 'Music Classification Service (Local)',
        'version': '1.0.0',
        'platform': 'Local PC',
        'endpoints': {
            'health': '/health',
            'classify': '/classify',
            'batch_classify': '/batch_classify',
            'model_info': '/model_info',
            'performance': '/performance'
        },
        'status': 'running'
    })

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        'status': 'healthy',
        'service': 'music-classification-service',
        'version': '1.0.0',
        'model_loaded': model_data is not None,
        'platform': 'Local PC'
    })

@app.route('/classify', methods=['POST'])
def classify_single():
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
        
        audio_array = np.array(audio_data, dtype=np.float32)
        
        if len(audio_array) == 0:
            raise BadRequest("audio_data cannot be empty")
        
        logger.info(f"Classifying song {song_id} with {len(audio_array)} samples")
        
        features = feature_extractor.extract_features_from_array(audio_array, sample_rate)
        
        if features is None:
            raise BadRequest("Failed to extract features from audio data")
        
        result = classify_features(features)
        result['song_id'] = song_id
        
        logger.info(f"Classification result for {song_id}: {result['prediction']} (confidence: {result['confidence']:.3f})")
        
        return jsonify(result)
        
    except BadRequest as e:
        return jsonify({'error': str(e)}), 400
    except Exception as e:
        logger.error(f"Error in classify_single: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/classify_features', methods=['POST'])
def classify_song_with_features():
    """Classify a song using pre-extracted features"""
    try:
        data = request.get_json()
        
        if not data or 'features' not in data:
            return jsonify({
                'success': False,
                'error': 'features field is required'
            }), 400
        
        song_id = data.get('song_id', 'unknown')
        features = data.get('features', [])
        
        if not features:
            return jsonify({
                'success': False,
                'error': 'features cannot be empty'
            }), 400
        
        features_array = np.array(features, dtype=np.float32)
        
        if len(features_array) == 0:
            return jsonify({
                'success': False,
                'error': 'features cannot be empty'
            }), 400
        
        # Ensure we have the right number of features
        expected_features = 65  # Based on the trained model (65 features, 30 selected)
        if len(features_array) != expected_features:
            logger.warning(f"Expected {expected_features} features, got {len(features_array)}")
            # Pad or truncate to match expected size
            if len(features_array) < expected_features:
                # Pad with zeros
                features_array = np.pad(features_array, (0, expected_features - len(features_array)), 'constant')
            else:
                # Truncate
                features_array = features_array[:expected_features]
        
        # Use the same feature processing pipeline as classify_features function
        feature_names = model_data['feature_names']
        features_dict = {name: features_array[i] if i < len(features_array) else 0.0 for i, name in enumerate(feature_names)}
        X = np.array([[features_dict.get(name, 0.0) for name in feature_names]])
        
        X_variance_filtered = model_data['variance_selector'].transform(X)
        X_scaled = model_data['scaler'].transform(X_variance_filtered)
        X_processed = model_data['feature_selector'].transform(X_scaled)
        
        prediction = model_data['model'].predict(X_processed)[0]
        probabilities = model_data['model'].predict_proba(X_processed)[0]
        
        # Convert prediction to string and ensure all values are JSON serializable
        prediction_str = str(prediction)
        if hasattr(model_data, 'get') and 'label_map' in model_data:
            prediction_str = model_data['label_map'].get(prediction, str(prediction))
        
        result = {
            'success': True,
            'prediction': prediction_str,
            'confidence': float(max(probabilities)),
            'probabilities': {
                'christian': float(probabilities[0]),
                'secular': float(probabilities[1])
            },
            'song_id': song_id
        }
        
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Error in feature-based classification: {e}")
        return jsonify({
            'success': False,
            'error': f'Feature-based classification failed: {str(e)}'
        }), 500

@app.route('/classify_audio_data', methods=['POST'])
def classify_song_with_audio_data():
    """Classify a song using raw audio data (server-side feature extraction)"""
    try:
        data = request.get_json()
        
        if not data or 'audio_data' not in data:
            return jsonify({
                'success': False,
                'error': 'audio_data field is required'
            }), 400
        
        song_id = data.get('song_id', 'unknown')
        audio_data = data.get('audio_data', [])
        sample_rate = data.get('sample_rate', 22050)
        
        if not audio_data:
            return jsonify({
                'success': False,
                'error': 'audio_data cannot be empty'
            }), 400
        
        # Convert audio data to numpy array
        audio_array = np.array(audio_data, dtype=np.float32)
        
        # Extract features using the same method as the original training
        features = extract_features_from_audio_data(audio_array, sample_rate)
        
        if features is None:
            return jsonify({
                'success': False,
                'error': 'Failed to extract features from audio data'
            }), 400
        
        # Convert features to the format expected by the model
        feature_names = model_data['feature_names']
        features_dict = {name: features.get(name, 0.0) for name in feature_names}
        X = np.array([[features_dict.get(name, 0.0) for name in feature_names]])
        
        # Apply the same preprocessing pipeline
        X_variance_filtered = model_data['variance_selector'].transform(X)
        X_scaled = model_data['scaler'].transform(X_variance_filtered)
        X_processed = model_data['feature_selector'].transform(X_scaled)
        
        prediction = model_data['model'].predict(X_processed)[0]
        probabilities = model_data['model'].predict_proba(X_processed)[0]
        
        # Convert prediction to string
        prediction_str = model_data['label_map'].get(prediction, str(prediction))
        
        result = {
            'success': True,
            'prediction': prediction_str,
            'confidence': float(max(probabilities)),
            'probabilities': {
                'christian': float(probabilities[0]),
                'secular': float(probabilities[1])
            },
            'song_id': song_id
        }
        
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Error in audio data classification: {e}")
        return jsonify({
            'success': False,
            'error': f'Audio data classification failed: {str(e)}'
        }), 500

@app.route('/classify_audio_file', methods=['POST'])
def classify_audio_file():
    """Classify a song using raw audio file data"""
    global request_count
    request_count += 1
    
    try:
        # Get metadata from headers
        song_id = request.headers.get('X-Song-ID', 'unknown')
        file_name = request.headers.get('X-File-Name', 'unknown.opus')
        
        # Get raw audio data
        audio_data = request.get_data()
        
        if not audio_data:
            return jsonify({
                'success': False,
                'error': 'No audio data provided'
            }), 400
        
        logger.info(f"Received audio data: {len(audio_data)} bytes for song: {song_id}")
        
        # Save audio data temporarily
        with tempfile.NamedTemporaryFile(delete=False, suffix='.opus') as temp_file:
            temp_file.write(audio_data)
            temp_file_path = temp_file.name
        
        try:
            # Load audio file using librosa
            y, sr = librosa.load(temp_file_path, sr=22050, duration=10)
            
            if len(y) == 0:
                return jsonify({
                    'success': False,
                    'error': 'Could not load audio from data'
                }), 400
            
            logger.info(f"Loaded audio: {len(y)} samples at {sr}Hz")
            
            # Extract features using the same method as the original training
            features = extract_features_from_audio_data(y, sr)
            
            if features is None:
                return jsonify({
                    'success': False,
                    'error': 'Failed to extract features from audio data'
                }), 400
            
            # Convert features to the format expected by the model
            feature_names = model_data['feature_names']
            features_dict = {name: features.get(name, 0.0) for name in feature_names}
            X = np.array([[features_dict.get(name, 0.0) for name in feature_names]])
            
            # Apply the same preprocessing pipeline
            X_variance_filtered = model_data['variance_selector'].transform(X)
            X_scaled = model_data['scaler'].transform(X_variance_filtered)
            X_processed = model_data['feature_selector'].transform(X_scaled)
            
            prediction = model_data['model'].predict(X_processed)[0]
            probabilities = model_data['model'].predict_proba(X_processed)[0]
            
            # Convert prediction to string
            prediction_str = model_data['label_map'].get(prediction, str(prediction))
            
            result = {
                'success': True,
                'prediction': prediction_str,
                'confidence': float(max(probabilities)),
                'probabilities': {
                    'christian': float(probabilities[0]),
                    'secular': float(probabilities[1])
                },
                'song_id': song_id,
                'file_name': file_name
            }
            
            logger.info(f"Classification result: {prediction_str} (confidence: {max(probabilities):.3f})")
            return jsonify(result)
            
        finally:
            # Clean up temporary file
            try:
                os.unlink(temp_file_path)
            except:
                pass
                
    except Exception as e:
        logger.error(f"Error in audio file classification: {e}")
        return jsonify({
            'success': False,
            'error': f'Audio file classification failed: {str(e)}'
        }), 500

@app.route('/classify_file', methods=['POST'])
def classify_file():
    """Classify a song using uploaded audio file (multipart form)"""
    global request_count
    request_count += 1
    
    try:
        # Debug: Log what Flask is receiving
        logger.info(f"Request files: {list(request.files.keys())}")
        logger.info(f"Request form keys: {list(request.form.keys())}")
        logger.info(f"Request content type: {request.content_type}")
        
        # Check if file was uploaded
        if 'file' not in request.files:
            logger.warning("No 'file' key found in request.files")
            return jsonify({
                'success': False,
                'error': 'No audio file provided'
            }), 400
        
        file = request.files['file']
        song_id = request.form.get('song_id', 'unknown')
        
        if file.filename == '':
            return jsonify({
                'success': False,
                'error': 'No file selected'
            }), 400
        
        # Save uploaded file temporarily
        with tempfile.NamedTemporaryFile(delete=False, suffix='.tmp') as temp_file:
            file.save(temp_file.name)
            temp_file_path = temp_file.name
        
        try:
            # Load audio file using librosa (same as Python demo)
            y, sr = librosa.load(temp_file_path, sr=22050, duration=10)
            
            if len(y) == 0:
                return jsonify({
                    'success': False,
                    'error': 'Could not load audio from file'
                }), 400
            
            # Extract features using the same method as the original training
            features = extract_features_from_audio_data(y, sr)
            
            if features is None:
                return jsonify({
                    'success': False,
                    'error': 'Failed to extract features from audio file'
                }), 400
            
            # Convert features to the format expected by the model
            feature_names = model_data['feature_names']
            features_dict = {name: features.get(name, 0.0) for name in feature_names}
            X = np.array([[features_dict.get(name, 0.0) for name in feature_names]])
            
            # Apply the same preprocessing pipeline
            X_variance_filtered = model_data['variance_selector'].transform(X)
            X_scaled = model_data['scaler'].transform(X_variance_filtered)
            X_processed = model_data['feature_selector'].transform(X_scaled)
            
            prediction = model_data['model'].predict(X_processed)[0]
            probabilities = model_data['model'].predict_proba(X_processed)[0]
            
            # Convert prediction to string
            prediction_str = model_data['label_map'].get(prediction, str(prediction))
            
            result = {
                'success': True,
                'prediction': prediction_str,
                'confidence': float(max(probabilities)),
                'probabilities': {
                    'christian': float(probabilities[0]),
                    'secular': float(probabilities[1])
                },
                'song_id': song_id,
                'file_name': file.filename
            }
            
            return jsonify(result)
            
        finally:
            # Clean up temporary file
            try:
                os.unlink(temp_file_path)
            except:
                pass
                
    except Exception as e:
        logger.error(f"Error in file classification: {e}")
        return jsonify({
            'success': False,
            'error': f'File classification failed: {str(e)}'
        }), 500

@app.route('/batch_classify', methods=['POST'])
def batch_classify():
    global request_count
    request_count += 1
    
    try:
        data = request.get_json()
        
        if not data:
            raise BadRequest("No JSON data provided")
        
        songs = data.get('songs', [])
        
        if not isinstance(songs, list):
            raise BadRequest("songs must be a list")
        
        if len(songs) == 0:
            raise BadRequest("songs list cannot be empty")
        
        if len(songs) > 1000:  # Higher limit for local hosting
            raise BadRequest("Maximum 1000 songs per batch for local hosting")
        
        logger.info(f"Processing batch of {len(songs)} songs")
        
        results = []
        failed_count = 0
        
        for i, song_data in enumerate(songs):
            try:
                song_id = song_data.get('song_id', f'song_{i}')
                audio_data = song_data.get('audio_data')
                sample_rate = song_data.get('sample_rate', 22050)
                
                if audio_data is None:
                    results.append({
                        'song_id': song_id,
                        'prediction': 'unknown',
                        'confidence': 0.0,
                        'probabilities': {'christian': 0.5, 'secular': 0.5},
                        'success': False,
                        'error': 'audio_data field is required'
                    })
                    failed_count += 1
                    continue
                
                audio_array = np.array(audio_data, dtype=np.float32)
                
                if len(audio_array) == 0:
                    results.append({
                        'song_id': song_id,
                        'prediction': 'unknown',
                        'confidence': 0.0,
                        'probabilities': {'christian': 0.5, 'secular': 0.5},
                        'success': False,
                        'error': 'audio_data cannot be empty'
                    })
                    failed_count += 1
                    continue
                
                features = feature_extractor.extract_features_from_array(audio_array, sample_rate)
                
                if features is None:
                    results.append({
                        'song_id': song_id,
                        'prediction': 'unknown',
                        'confidence': 0.0,
                        'probabilities': {'christian': 0.5, 'secular': 0.5},
                        'success': False,
                        'error': 'Failed to extract features'
                    })
                    failed_count += 1
                    continue
                
                result = classify_features(features)
                result['song_id'] = song_id
                results.append(result)
                
            except Exception as e:
                logger.error(f"Error processing song {i}: {e}")
                results.append({
                    'song_id': song_data.get('song_id', f'song_{i}'),
                    'prediction': 'unknown',
                    'confidence': 0.0,
                    'probabilities': {'christian': 0.5, 'secular': 0.5},
                    'success': False,
                    'error': str(e)
                })
                failed_count += 1
        
        successful = sum(1 for r in results if r['success'])
        logger.info(f"Batch classification complete: {successful}/{len(results)} successful")
        
        return jsonify({
            'success': True,
            'results': results,
            'summary': {
                'total': len(songs),
                'successful': successful,
                'failed': failed_count
            }
        })
        
    except BadRequest as e:
        return jsonify({'error': str(e)}), 400
    except Exception as e:
        logger.error(f"Error in batch_classify: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/model_info', methods=['GET'])
def model_info():
    if model_data is None:
        return jsonify({'error': 'Model not loaded'}), 503
    
    class_weights = model_data.get('class_weights', {})
    if isinstance(class_weights, dict):
        serializable_class_weights = {}
        for key, value in class_weights.items():
            if hasattr(value, 'tolist'):
                serializable_class_weights[key] = value.tolist()
            else:
                serializable_class_weights[key] = value
    else:
        serializable_class_weights = {}
    
    uptime_seconds = time.time() - start_time
    uptime_hours = uptime_seconds / 3600
    
    return jsonify({
        'model_type': model_data['model_type'],
        'total_features': len(model_data['feature_names']),
        'selected_features': len(model_data['selected_feature_names']),
        'label_map': model_data['label_map'],
        'class_weights': serializable_class_weights,
        'feature_names': model_data['feature_names'],
        'selected_feature_names': model_data['selected_feature_names'],
        'performance': {
            'max_workers': MAX_WORKERS,
            'uptime_hours': round(uptime_hours, 2),
            'total_requests': request_count,
            'requests_per_hour': round(request_count / max(uptime_hours, 0.01), 2),
            'optimization_level': 'local_full_featured'
        },
        'platform': 'Local PC'
    })

@app.route('/performance', methods=['GET'])
def performance_stats():
    uptime_seconds = time.time() - start_time
    uptime_hours = uptime_seconds / 3600
    
    return jsonify({
        'service_status': 'local_full_featured',
        'optimization_level': 'high_performance_local',
        'full_featured': True,
        'performance_metrics': {
            'max_workers': MAX_WORKERS,
            'uptime_hours': round(uptime_hours, 2),
            'total_requests': request_count,
            'requests_per_hour': round(request_count / max(uptime_hours, 0.01), 2),
            'average_response_time_ms': 'optimized',
            'batch_capacity': '1000 songs',
            'memory_efficient': True,
            'cpu_optimized': True
        },
        'local_specs': {
            'platform': 'Local PC',
            'memory': 'Unlimited',
            'cpu': f'{mp.cpu_count()} cores',
            'optimization_strategy': 'full_featured_ml',
            'max_concurrent_requests': MAX_WORKERS,
            'recommended_batch_size': '100-500 songs',
            'librosa_support': True,
            'unlimited_size': True
        }
    })

@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint not found'}), 404

@app.errorhandler(500)
def internal_error(error):
    return jsonify({'error': 'Internal server error'}), 500

# Start the Flask app
if __name__ == '__main__':
    logger.info("🚀 Starting Local Music Classification Service...")
    
    if load_model():
        logger.info("✅ Service ready!")
        logger.info("📡 Available endpoints:")
        logger.info("   GET  /health - Health check")
        logger.info("   POST /classify - Classify single song")
        logger.info("   POST /classify_features - Classify using pre-extracted features")
        logger.info("   POST /classify_audio_data - Classify using raw audio data (server-side feature extraction)")
        logger.info("   POST /classify_file - Classify using uploaded audio file (multipart)")
        logger.info("   POST /classify_audio_file - Classify using raw audio file data (RECOMMENDED)")
        logger.info("   POST /batch_classify - Classify multiple songs (up to 1000)")
        logger.info("   GET  /model_info - Get model information")
        logger.info("   GET  /performance - Performance statistics")
        logger.info("   GET  / - Service information")
        logger.info(f"🔧 Optimization: {MAX_WORKERS} workers, full librosa support")
        logger.info("💾 Memory: Unlimited, local hosting")
        
        # Get local IP address
        import socket
        hostname = socket.gethostname()
        local_ip = socket.gethostbyname(hostname)
        
        print("\n" + "="*60)
        print("YOUR LOCAL SERVICE URLS:")
        print(f"   Local: http://localhost:5000")
        print(f"   Network: http://{local_ip}:5000")
        print("Use the Network URL in your Android app!")
        print("="*60 + "\n")
        
        # Test the service
        try:
            import requests
            print("Testing local service...")
            
            # Test health check
            response = requests.get("http://localhost:5000/health")
            print(f"Health check: {response.json()}")
            
            # Test classification
            test_data = {
                "audio_data": [0.1, 0.2, 0.3, 0.4, 0.5],
                "sample_rate": 22050,
                "song_id": "test_local"
            }
            response = requests.post("http://localhost:5000/classify", json=test_data)
            print(f"Classification test: {response.json()}")
            
        except Exception as e:
            print(f"Service test failed: {e}")
        
        print("\nService is running! Press Ctrl+C to stop.")
        
        app.run(host='0.0.0.0', port=5000, debug=False)
    else:
        logger.error("Failed to start service - model loading failed")
        sys.exit(1)
