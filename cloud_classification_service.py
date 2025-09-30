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

class CloudAudioFeatureExtractor:
    """Cloud-based audio feature extractor matching the working model."""
    
    def __init__(self, sample_rate: int = 22050, duration: int = 10):
        """Initialize feature extractor with same parameters as working model."""
        self.sample_rate = sample_rate
        self.duration = duration
        
    def extract_features_from_array(self, audio_data: np.ndarray, sample_rate: int) -> Optional[Dict[str, float]]:
        """
        Extract features from audio data array.
        
        Args:
            audio_data: Audio data as numpy array
            sample_rate: Sample rate of the audio
            
        Returns:
            Dictionary of extracted features or None if failed
        """
        try:
            # Resample if necessary
            if sample_rate != self.sample_rate:
                y = librosa.resample(audio_data, orig_sr=sample_rate, target_sr=self.sample_rate)
            else:
                y = audio_data
            
            # Truncate or pad to desired duration
            target_length = self.sample_rate * self.duration
            if len(y) > target_length:
                y = y[:target_length]
            elif len(y) < target_length:
                y = np.pad(y, (0, target_length - len(y)), mode='constant')
            
            if len(y) == 0:
                return None
            
            features = {}
            
            # Basic properties (normalized/relative features)
            features['signal_length_ratio'] = float(len(y) / (self.sample_rate * self.duration))
            features['rms_energy_ratio'] = float(np.sqrt(np.mean(y**2)) / (np.max(np.abs(y)) + 1e-8))
            
            # 1. Spectral features
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
            
            # 2. Zero crossing rate
            zcr = librosa.feature.zero_crossing_rate(y)[0]
            features['zcr_mean'] = float(np.mean(zcr))
            features['zcr_std'] = float(np.std(zcr))
            
            # 3. MFCC features (first 13 coefficients)
            mfccs = librosa.feature.mfcc(y=y, sr=self.sample_rate, n_mfcc=13)
            for i in range(13):
                features[f'mfcc_{i+1}_mean'] = float(np.mean(mfccs[i]))
                features[f'mfcc_{i+1}_std'] = float(np.std(mfccs[i]))
            
            # 4. Chroma features (key-related)
            chroma = librosa.feature.chroma_stft(y=y, sr=self.sample_rate)
            features['chroma_mean'] = float(np.mean(chroma))
            features['chroma_std'] = float(np.std(chroma))
            
            # Individual chroma bins (12 semitones)
            chroma_bins = np.mean(chroma, axis=1)
            for i in range(12):
                features[f'chroma_bin_{i}'] = float(chroma_bins[i])
            
            # 5. Tonnetz features (harmonic network)
            tonnetz = librosa.feature.tonnetz(y=y, sr=self.sample_rate)
            features['tonnetz_mean'] = float(np.mean(tonnetz))
            features['tonnetz_std'] = float(np.std(tonnetz))
            
            # 6. Rhythm and tempo features
            tempo, beats = librosa.beat.beat_track(y=y, sr=self.sample_rate)
            features['tempo'] = float(tempo) if np.isfinite(tempo) else 120.0
            features['beat_strength'] = float(len(beats) / (len(y) / self.sample_rate)) if len(y) > 0 else 0.0
            
            # 7. Spectral contrast
            contrast = librosa.feature.spectral_contrast(y=y, sr=self.sample_rate)
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
            features['spectral_centroid_normalized'] = float(np.mean(spectral_centroids) / (self.sample_rate / 2))
            
            # 12. Zero-padding and windowing artifacts detection
            features['silence_ratio'] = float(np.sum(np.abs(y) < 0.01) / len(y))
            
            return features
            
        except Exception as e:
            logger.error(f"Error extracting features: {e}")
            return None
    
    def _skewness(self, data):
        """Calculate skewness of data."""
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
            'model_info': '/model_info'
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
    try:
        data = request.get_json()
        
        if not data:
            raise BadRequest("No JSON data provided")
        
        songs = data.get('songs', [])
        
        if not isinstance(songs, list):
            raise BadRequest("songs must be a list")
        
        if len(songs) == 0:
            raise BadRequest("songs list cannot be empty")
        
        if len(songs) > 50:  # Limit batch size
            raise BadRequest("Maximum 50 songs per batch")
        
        logger.info(f"Batch classifying {len(songs)} songs")
        
        results = []
        
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
                    continue
                
                # Convert to numpy array
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
                    continue
                
                # Extract features
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
                    continue
                
                # Classify
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
        
        successful = sum(1 for r in results if r['success'])
        logger.info(f"Batch classification complete: {successful}/{len(results)} successful")
        
        return jsonify({
            'results': results,
            'summary': {
                'total': len(results),
                'successful': successful,
                'failed': len(results) - successful
            }
        })
        
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
    
    return jsonify({
        'model_type': model_data['model_type'],
        'total_features': len(model_data['feature_names']),
        'selected_features': len(model_data['selected_feature_names']),
        'label_map': model_data['label_map'],
        'class_weights': serializable_class_weights,
        'feature_names': model_data['feature_names'],
        'selected_feature_names': model_data['selected_feature_names']
    })

@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint not found'}), 404

@app.errorhandler(500)
def internal_error(error):
    return jsonify({'error': 'Internal server error'}), 500

if __name__ == '__main__':
    # Load model on startup
    if not load_model():
        logger.error("Failed to load model. Exiting.")
        sys.exit(1)
    
    # Get port from environment variable (for Render.com)
    port = int(os.environ.get('PORT', 5000))
    
    logger.info(f"🚀 Starting Music Classification Service on port {port}")
    logger.info("📡 Available endpoints:")
    logger.info("   GET  /health - Health check")
    logger.info("   POST /classify - Classify single song")
    logger.info("   POST /batch_classify - Classify multiple songs")
    logger.info("   GET  /model_info - Model information")
    
    app.run(host='0.0.0.0', port=port, debug=False)
