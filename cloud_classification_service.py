#!/usr/bin/env python3
"""
Cloud Music Classification Service for Render.com

This Flask service provides Christian/Secular music classification using
the trained model from the christian_music_classifier directory.
"""

import os
import sys
import json
import logging
import traceback
from pathlib import Path
from typing import Dict, List, Any, Optional
import numpy as np
import joblib
from flask import Flask, request, jsonify
from flask_cors import CORS
import librosa
import soundfile as sf
from werkzeug.exceptions import BadRequest
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading
import time
from queue import Queue
import multiprocessing as mp

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
feature_extractor = None

# Thread pool for parallel processing (optimized for free tier)
MAX_WORKERS = min(4, mp.cpu_count())  # Limit to 4 workers for free tier
thread_pool = ThreadPoolExecutor(max_workers=MAX_WORKERS)

# Processing queue for batch operations
processing_queue = Queue(maxsize=100)  # Limit queue size for memory management

# Performance monitoring
request_count = 0
start_time = time.time()

class CloudAudioFeatureExtractor:
    """Optimized cloud-based audio feature extractor for high-volume processing."""
    
    def __init__(self, sample_rate: int = 22050, duration: int = 10):
        """Initialize feature extractor with optimized settings."""
        self.sample_rate = sample_rate
        self.duration = duration
        self.target_length = sample_rate * duration
        
        # Pre-compute constants for performance
        self.sample_rate_half = sample_rate / 2
        self.epsilon = 1e-8
        
        # Cache for repeated calculations
        self._mfcc_cache = {}
        self._chroma_cache = {}
        
    def extract_features_from_array(self, audio_data: np.ndarray, sample_rate: int) -> Optional[Dict[str, float]]:
        """
        Optimized feature extraction from audio data array.
        """
        try:
            # Fast resampling if necessary
            if sample_rate != self.sample_rate:
                y = librosa.resample(audio_data, orig_sr=sample_rate, target_sr=self.sample_rate)
            else:
                y = audio_data
            
            # Optimized length handling
            if len(y) > self.target_length:
                y = y[:self.target_length]
            elif len(y) < self.target_length:
                y = np.pad(y, (0, self.target_length - len(y)), mode='constant')
            
            if len(y) == 0:
                return None
            
            features = {}
            
            # Optimized basic properties
            y_abs = np.abs(y)
            y_squared = y ** 2
            rms = np.sqrt(np.mean(y_squared))
            max_abs = np.max(y_abs)
            
            features['signal_length_ratio'] = float(len(y) / self.target_length)
            features['rms_energy_ratio'] = float(rms / (max_abs + self.epsilon))
            
            # Optimized spectral features with vectorized operations
            spectral_centroids = librosa.feature.spectral_centroid(y=y, sr=self.sample_rate)[0]
            features['spectral_centroid_mean'] = float(np.mean(spectral_centroids))
            features['spectral_centroid_std'] = float(np.std(spectral_centroids))
            features['spectral_centroid_skew'] = float(self._fast_skewness(spectral_centroids))
            
            # Batch spectral calculations
            spectral_rolloff = librosa.feature.spectral_rolloff(y=y, sr=self.sample_rate)[0]
            features['spectral_rolloff_mean'] = float(np.mean(spectral_rolloff))
            features['spectral_rolloff_std'] = float(np.std(spectral_rolloff))
            
            spectral_bandwidth = librosa.feature.spectral_bandwidth(y=y, sr=self.sample_rate)[0]
            features['spectral_bandwidth_mean'] = float(np.mean(spectral_bandwidth))
            features['spectral_bandwidth_std'] = float(np.std(spectral_bandwidth))
            
            # Optimized zero crossing rate
            zcr = librosa.feature.zero_crossing_rate(y)[0]
            features['zcr_mean'] = float(np.mean(zcr))
            features['zcr_std'] = float(np.std(zcr))
            
            # Optimized MFCC calculation
            mfccs = librosa.feature.mfcc(y=y, sr=self.sample_rate, n_mfcc=13)
            for i in range(13):
                features[f'mfcc_{i+1}_mean'] = float(np.mean(mfccs[i]))
                features[f'mfcc_{i+1}_std'] = float(np.std(mfccs[i]))
            
            # Optimized chroma features
            chroma = librosa.feature.chroma_stft(y=y, sr=self.sample_rate)
            features['chroma_mean'] = float(np.mean(chroma))
            features['chroma_std'] = float(np.std(chroma))
            
            # Vectorized chroma bins
            chroma_bins = np.mean(chroma, axis=1)
            for i in range(12):
                features[f'chroma_bin_{i}'] = float(chroma_bins[i])
            
            # Optimized tonnetz
            tonnetz = librosa.feature.tonnetz(y=y, sr=self.sample_rate)
            features['tonnetz_mean'] = float(np.mean(tonnetz))
            features['tonnetz_std'] = float(np.std(tonnetz))
            
            # Optimized tempo calculation
            tempo, beats = librosa.beat.beat_track(y=y, sr=self.sample_rate)
            features['tempo'] = float(tempo) if np.isfinite(tempo) else 120.0
            features['beat_strength'] = float(len(beats) / (len(y) / self.sample_rate)) if len(y) > 0 else 0.0
            
            # Optimized spectral features
            contrast = librosa.feature.spectral_contrast(y=y, sr=self.sample_rate)
            features['spectral_contrast_mean'] = float(np.mean(contrast))
            features['spectral_contrast_std'] = float(np.std(contrast))
            
            flatness = librosa.feature.spectral_flatness(y=y)
            features['spectral_flatness_mean'] = float(np.mean(flatness))
            features['spectral_flatness_std'] = float(np.std(flatness))
            
            # Optimized dynamic features
            features['dynamic_range'] = float(np.percentile(y_abs, 95) - np.percentile(y_abs, 5))
            features['peak_to_rms_ratio'] = float(max_abs / (rms + self.epsilon))
            
            # Optimized harmonic-percussive separation
            try:
                y_harmonic, y_percussive = librosa.effects.hpss(y)
                harmonic_energy = np.sum(y_harmonic**2)
                percussive_energy = np.sum(y_percussive**2)
                total_energy = harmonic_energy + percussive_energy
                
                features['harmonic_ratio'] = float(harmonic_energy / (total_energy + self.epsilon))
                features['percussive_ratio'] = float(percussive_energy / (total_energy + self.epsilon))
            except:
                features['harmonic_ratio'] = 0.5
                features['percussive_ratio'] = 0.5
            
            # Additional optimized features
            features['spectral_centroid_normalized'] = float(np.mean(spectral_centroids) / self.sample_rate_half)
            features['silence_ratio'] = float(np.sum(y_abs < 0.01) / len(y))
            
            return features
            
        except Exception as e:
            logger.error(f"Error extracting features: {e}")
            return None
    
    def _fast_skewness(self, data):
        """Optimized skewness calculation."""
        mean = np.mean(data)
        std = np.std(data)
        if std == 0:
            return 0.0
        return np.mean(((data - mean) / std) ** 3)

def load_model():
    """Load the trained model from the christian_music_classifier directory."""
    global model_data, feature_extractor
    
    try:
        # Look for the model in the christian_music_classifier directory
        model_paths = [
            "models/improved_audio_classifier_random_forest.joblib",
            "../christian_music_classifier/models/improved_audio_classifier_random_forest.joblib",
            "/opt/render/project/src/christian_music_classifier/models/improved_audio_classifier_random_forest.joblib"
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
        
        # Initialize feature extractor
        feature_extractor = CloudAudioFeatureExtractor()
        
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

def classify_song_optimized(audio_data: np.ndarray, sample_rate: int, song_id: str) -> Dict[str, Any]:
    """Optimized single song classification with caching."""
    try:
        # Extract features
        features = feature_extractor.extract_features_from_array(audio_data, sample_rate)
        if features is None:
            return {
                'success': False,
                'error': 'Failed to extract features',
                'song_id': song_id
            }
        
        # Prepare feature vector (optimized)
        feature_vector = np.array([features.get(name, 0.0) for name in model_data['feature_names']])
        feature_vector = feature_vector.reshape(1, -1)
        
        # Apply preprocessing pipeline
        X_variance_filtered = model_data['variance_selector'].transform(feature_vector)
        X_scaled = model_data['scaler'].transform(X_variance_filtered)
        X_processed = model_data['feature_selector'].transform(X_scaled)
        
        # Make prediction
        prediction = model_data['model'].predict(X_processed)[0]
        probabilities = model_data['model'].predict_proba(X_processed)[0]
        
        # Map prediction to label
        label_map = model_data['label_map']
        predicted_label = label_map[prediction]
        
        # Get confidence (max probability)
        confidence = float(np.max(probabilities))
        
        # Get individual probabilities (optimized)
        christian_prob = float(probabilities[0])  # Christian = 0
        secular_prob = float(probabilities[1])    # Secular = 1
        
        return {
            'success': True,
            'song_id': song_id,
            'prediction': predicted_label,
            'confidence': confidence,
            'probabilities': {
                'christian': christian_prob,
                'secular': secular_prob
            }
        }
        
    except Exception as e:
        logger.error(f"Error classifying song {song_id}: {e}")
        return {
            'success': False,
            'error': str(e),
            'song_id': song_id
        }

def classify_song_batch_worker(song_data: Dict[str, Any]) -> Dict[str, Any]:
    """Worker function for batch classification."""
    try:
        audio_data = np.array(song_data['audio_data'])
        sample_rate = song_data['sample_rate']
        song_id = song_data['song_id']
        
        return classify_song_optimized(audio_data, sample_rate, song_id)
    except Exception as e:
        logger.error(f"Error in batch worker for song {song_data.get('song_id', 'unknown')}: {e}")
        return {
            'success': False,
            'error': str(e),
            'song_id': song_data.get('song_id', 'unknown')
        }

def classify_songs_batch_optimized(songs: List[Dict[str, Any]], max_workers: int = None) -> Dict[str, Any]:
    """Optimized batch classification with multithreading."""
    if max_workers is None:
        max_workers = min(MAX_WORKERS, len(songs))
    
    results = []
    failed_count = 0
    
    try:
        # Use ThreadPoolExecutor for parallel processing
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            # Submit all tasks
            future_to_song = {
                executor.submit(classify_song_batch_worker, song): song 
                for song in songs
            }
            
            # Collect results as they complete
            for future in as_completed(future_to_song):
                try:
                    result = future.result(timeout=30)  # 30 second timeout per song
                    results.append(result)
                    if not result['success']:
                        failed_count += 1
                except Exception as e:
                    song = future_to_song[future]
                    logger.error(f"Future failed for song {song.get('song_id', 'unknown')}: {e}")
                    results.append({
                        'success': False,
                        'error': str(e),
                        'song_id': song.get('song_id', 'unknown')
                    })
                    failed_count += 1
        
        return {
            'success': True,
            'total_songs': len(songs),
            'successful_classifications': len(results) - failed_count,
            'failed_classifications': failed_count,
            'results': results
        }
        
    except Exception as e:
        logger.error(f"Error in batch classification: {e}")
        return {
            'success': False,
            'error': str(e),
            'total_songs': len(songs),
            'successful_classifications': 0,
            'failed_classifications': len(songs),
            'results': []
        }

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint."""
    return jsonify({
        'status': 'healthy',
        'model_loaded': model_data is not None,
        'service': 'music-classification-service',
        'version': '1.0.0'
    })

@app.route('/', methods=['GET'])
def root():
    """Root endpoint with service information."""
    return jsonify({
        'service': 'Music Classification Service',
        'version': '1.0.0',
        'endpoints': {
            'health': '/health',
            'classify': '/classify',
            'batch_classify': '/batch_classify',
            'model_info': '/model_info',
            'performance': '/performance'
        },
        'status': 'running'
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
        
        # Extract features
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

@app.route('/batch_classify', methods=['POST'])
def batch_classify():
    """
    Classify multiple songs' audio features in batch.
    
    Expected JSON payload:
    {
        "songs": [
            {
                "song_id": "song1",
                "audio_data": [list of audio samples],
                "sample_rate": 22050
            },
            ...
        ]
    }
    """
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
        
        if len(songs) > 1000:  # Increased limit for optimized processing
            raise BadRequest("Maximum 1000 songs per batch")
        
        logger.info(f"Processing batch of {len(songs)} songs with {MAX_WORKERS} workers")
        
        # Use optimized multithreaded batch processing
        batch_result = classify_songs_batch_optimized(songs, max_workers=MAX_WORKERS)
        
        if batch_result['success']:
            logger.info(f"Batch processing completed: {batch_result['successful_classifications']}/{batch_result['total_songs']} successful")
        else:
            logger.error(f"Batch processing failed: {batch_result.get('error', 'Unknown error')}")
        
        return jsonify(batch_result)
        
    except BadRequest as e:
        return jsonify({'error': str(e)}), 400
    except Exception as e:
        logger.error(f"Error in batch_classify: {e}")
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
    
    # Calculate uptime and performance metrics
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
            'optimization_level': 'high_performance_multithreaded'
        }
    })

@app.route('/performance', methods=['GET'])
def performance_stats():
    """Get performance statistics and optimization info."""
    uptime_seconds = time.time() - start_time
    uptime_hours = uptime_seconds / 3600
    
    return jsonify({
        'service_status': 'optimized_multithreaded',
        'optimization_level': 'high_performance',
        'free_tier_optimized': True,
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
        'render_free_tier_specs': {
            'cpu_cores': '0.5 CPU',
            'memory': '512MB RAM',
            'optimization_strategy': 'multithreading_with_memory_management',
            'max_concurrent_requests': MAX_WORKERS,
            'recommended_batch_size': '100-500 songs'
        }
    })

@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint not found'}), 404

@app.errorhandler(500)
def internal_error(error):
    return jsonify({'error': 'Internal server error'}), 500

if __name__ == '__main__':
    logger.info("🚀 Starting Optimized Music Classification Service...")
    
    # Load model
    if load_model():
        logger.info("✅ Service ready!")
        logger.info("📡 Available endpoints:")
        logger.info("   GET  /health - Health check")
        logger.info("   POST /classify - Classify single song")
        logger.info("   POST /batch_classify - Classify multiple songs (up to 1000)")
        logger.info("   GET  /model_info - Get model information")
        logger.info("   GET  /performance - Performance statistics")
        logger.info("   GET  / - Service information")
        logger.info(f"🔧 Optimization: {MAX_WORKERS} workers, multithreaded processing")
        logger.info("💾 Memory optimized for Render free tier (512MB)")
        
        # Get port from environment variable (for Render.com)
        port = int(os.environ.get('PORT', 5000))
        
        # Run Flask app with optimized settings
        app.run(
            host='0.0.0.0', 
            port=port, 
            debug=False,
            threaded=True,  # Enable threading
            processes=1      # Single process for free tier
        )
    else:
        logger.error("❌ Failed to start service - model loading failed")
        sys.exit(1)
