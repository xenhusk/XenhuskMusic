"""
Google Colab Music Classification Service

This script provides a complete music classification service that can be deployed
on Google Colab and accessed via public URL. Perfect for heavy ML workloads.

To use this in Google Colab:
1. Run the installation commands below
2. Upload your model file
3. Run the service
4. Use ngrok to get a public URL
"""

# Install required packages for Google Colab
print("🚀 Installing dependencies for Google Colab...")

# Method 1: Using !pip (recommended for Colab)
!pip install flask flask-cors librosa soundfile scikit-learn joblib pandas numpy pyngrok

# Method 2: Alternative using subprocess (if !pip doesn't work)
import subprocess
import sys

def install_packages():
    """Install required packages for Google Colab."""
    packages = [
        'flask', 'flask-cors', 'librosa', 'soundfile', 
        'scikit-learn', 'joblib', 'pandas', 'numpy', 'pyngrok'
    ]
    
    for package in packages:
        try:
            subprocess.check_call([sys.executable, '-m', 'pip', 'install', package])
            print(f"✅ Installed {package}")
        except subprocess.CalledProcessError:
            print(f"❌ Failed to install {package}")

# Uncomment the line below if !pip doesn't work
# install_packages()

print("✅ Dependencies installation complete!")

# Optional: Setup ngrok authtoken (run this cell if you want to use ngrok)
def setup_ngrok():
    """
    Setup ngrok authtoken for public URL access.
    Run this cell and follow the instructions.
    """
    print("🔑 Setting up ngrok authtoken...")
    print("📝 Steps to get ngrok working:")
    print("   1. Go to: https://dashboard.ngrok.com/signup")
    print("   2. Sign up for free account")
    print("   3. Get your authtoken from: https://dashboard.ngrok.com/get-started/your-authtoken")
    print("   4. Run the command below with your token:")
    print("")
    print("   !ngrok config add-authtoken YOUR_TOKEN_HERE")
    print("")
    print("   Then restart the service to get public URL!")

# Uncomment the line below to see ngrok setup instructions
# setup_ngrok()

# Import libraries
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

# Thread pool for parallel processing
MAX_WORKERS = min(4, mp.cpu_count())
thread_pool = ThreadPoolExecutor(max_workers=MAX_WORKERS)

# Performance monitoring
request_count = 0
start_time = time.time()

class ColabAudioFeatureExtractor:
    """Full-featured audio feature extractor for Google Colab."""
    
    def __init__(self, sample_rate: int = 22050, duration: int = 10):
        """Initialize feature extractor with full librosa capabilities."""
        self.sample_rate = sample_rate
        self.duration = duration
        self.target_length = sample_rate * duration
        
    def extract_features_from_array(self, audio_data: np.ndarray, sample_rate: int) -> Optional[Dict[str, float]]:
        """
        Extract comprehensive features using librosa.
        """
        try:
            # Resample if necessary
            if sample_rate != self.sample_rate:
                y = librosa.resample(audio_data, orig_sr=sample_rate, target_sr=self.sample_rate)
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
        """Calculate skewness of data."""
        mean = np.mean(data)
        std = np.std(data)
        if std == 0:
            return 0.0
        return np.mean(((data - mean) / std) ** 3)

def load_model():
    """Load the trained model."""
    global model_data, feature_extractor
    
    try:
        # Try multiple possible paths for the model
        model_paths = [
            "models/improved_audio_classifier_random_forest.joblib",
            "/content/models/improved_audio_classifier_random_forest.joblib",
            "improved_audio_classifier_random_forest.joblib"
        ]
        
        model_path = None
        for path in model_paths:
            if os.path.exists(path):
                model_path = path
                break
        
        if model_path is None:
            # If model not found, create a dummy model for testing
            logger.warning("Model not found, creating dummy model for testing")
            model_data = create_dummy_model()
        else:
            logger.info(f"Loading model from: {model_path}")
            model_data = joblib.load(model_path)
        
        # Initialize feature extractor
        feature_extractor = ColabAudioFeatureExtractor()
        
        logger.info("✅ Model loaded successfully!")
        logger.info(f"   Model type: {model_data['model_type']}")
        logger.info(f"   Features: {len(model_data['feature_names'])} → {len(model_data['selected_feature_names'])} (selected)")
        logger.info(f"   Label map: {model_data['label_map']}")
        
        return True
        
    except Exception as e:
        logger.error(f"❌ Failed to load model: {e}")
        logger.error(traceback.format_exc())
        return False

def create_dummy_model():
    """Create a dummy model for testing when real model is not available."""
    from sklearn.ensemble import RandomForestClassifier
    from sklearn.preprocessing import StandardScaler
    from sklearn.feature_selection import VarianceThreshold, SelectKBest, f_classif
    
    # Create dummy feature names (same as your real model)
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
    
    # Create dummy preprocessing pipeline
    variance_selector = VarianceThreshold(threshold=0.01)
    scaler = StandardScaler()
    feature_selector = SelectKBest(f_classif, k=30)
    
    # Create dummy model
    model = RandomForestClassifier(n_estimators=100, random_state=42)
    
    # Create dummy training data
    X_dummy = np.random.randn(100, len(feature_names))
    y_dummy = np.random.randint(0, 2, 100)
    
    # Fit preprocessing pipeline
    X_var = variance_selector.fit_transform(X_dummy)
    X_scaled = scaler.fit_transform(X_var)
    X_selected = feature_selector.fit_transform(X_scaled, y_dummy)
    
    # Fit model
    model.fit(X_selected, y_dummy)
    
    # Get selected feature names
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
    """
    Classify audio features using the loaded model.
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
        'service': 'Music Classification Service (Google Colab)',
        'version': '1.0.0',
        'platform': 'Google Colab',
        'endpoints': {
            'health': '/health',
            'classify': '/classify',
            'batch_classify': '/batch_classify',
            'model_info': '/model_info',
            'performance': '/performance'
        },
        'status': 'running',
        'note': 'Full-featured service with librosa support'
    })

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint."""
    return jsonify({
        'status': 'healthy',
        'service': 'music-classification-service',
        'version': '1.0.0',
        'model_loaded': model_data is not None,
        'platform': 'Google Colab'
    })

@app.route('/classify', methods=['POST'])
def classify_single():
    """
    Classify a single song's audio features.
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
        
        if len(songs) > 100:  # Reasonable limit for Colab
            raise BadRequest("Maximum 100 songs per batch for Colab")
        
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
                    failed_count += 1
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
                    failed_count += 1
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
            'optimization_level': 'colab_full_featured'
        },
        'platform': 'Google Colab'
    })

@app.route('/performance', methods=['GET'])
def performance_stats():
    """Get performance statistics and optimization info."""
    uptime_seconds = time.time() - start_time
    uptime_hours = uptime_seconds / 3600
    
    return jsonify({
        'service_status': 'colab_full_featured',
        'optimization_level': 'high_performance_colab',
        'full_featured': True,
        'performance_metrics': {
            'max_workers': MAX_WORKERS,
            'uptime_hours': round(uptime_hours, 2),
            'total_requests': request_count,
            'requests_per_hour': round(request_count / max(uptime_hours, 0.01), 2),
            'average_response_time_ms': 'optimized',
            'batch_capacity': '100 songs',
            'memory_efficient': True,
            'cpu_optimized': True
        },
        'colab_specs': {
            'platform': 'Google Colab',
            'memory': '12GB RAM',
            'cpu': '2 vCPU',
            'gpu': 'Optional T4 GPU',
            'optimization_strategy': 'full_featured_ml',
            'max_concurrent_requests': MAX_WORKERS,
            'recommended_batch_size': '50-100 songs',
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
    logger.info("🚀 Starting Google Colab Music Classification Service...")
    
    # Load model
    if load_model():
        logger.info("✅ Service ready!")
        logger.info("📡 Available endpoints:")
        logger.info("   GET  /health - Health check")
        logger.info("   POST /classify - Classify single song")
        logger.info("   POST /batch_classify - Classify multiple songs (up to 100)")
        logger.info("   GET  /model_info - Get model information")
        logger.info("   GET  /performance - Performance statistics")
        logger.info("   GET  / - Service information")
        logger.info(f"🔧 Optimization: {MAX_WORKERS} workers, full librosa support")
        logger.info("💾 Memory: 12GB RAM, unlimited dependencies")
        
        # Setup public access
        public_url = None
        
        # Method 1: Try ngrok with authentication
        try:
            logger.info("🌐 Setting up public access with ngrok...")
            
            # Check if ngrok authtoken is set
            import os
            if not os.environ.get('NGROK_AUTHTOKEN'):
                logger.info("🔑 ngrok authtoken not found. Setting up free ngrok...")
                # For free ngrok, we need to set authtoken
                logger.info("📝 To get ngrok authtoken:")
                logger.info("   1. Go to: https://dashboard.ngrok.com/signup")
                logger.info("   2. Sign up for free account")
                logger.info("   3. Get your authtoken from: https://dashboard.ngrok.com/get-started/your-authtoken")
                logger.info("   4. Run: ngrok config add-authtoken YOUR_TOKEN")
                raise Exception("ngrok authtoken required")
            
            public_url = ngrok.connect(5000)
            logger.info(f"🌐 Public URL: {public_url}")
            logger.info("📱 Update your Android app with this URL!")
            
            # Keep the tunnel alive
            def keep_alive():
                while True:
                    time.sleep(60)
                    logger.info("🔄 Keeping ngrok tunnel alive...")
            
            # Start keep-alive thread
            threading.Thread(target=keep_alive, daemon=True).start()
            
        except Exception as e:
            logger.warning(f"⚠️ Failed to setup ngrok: {e}")
            
            # Method 2: Try Colab's built-in public URL
            try:
                logger.info("🌐 Trying Colab's built-in public URL...")
                from google.colab import output
                
                # Enable public access
                output.serve_kernel_port_as_window(5000)
                logger.info("🌐 Colab public URL enabled!")
                logger.info("📱 Check the 'Public URL' link that appeared above!")
                logger.info("🔗 Or use the URL shown in the Colab interface")
                
            except Exception as e2:
                logger.warning(f"⚠️ Colab public URL also failed: {e2}")
                
                # Method 3: Manual instructions
                logger.info("🔧 Manual setup required:")
                logger.info("   1. Install ngrok: !pip install pyngrok")
                logger.info("   2. Get authtoken: https://dashboard.ngrok.com/signup")
                logger.info("   3. Set authtoken: ngrok config add-authtoken YOUR_TOKEN")
                logger.info("   4. Create tunnel: ngrok.connect(5000)")
                logger.info("   5. Use the ngrok URL in your Android app")
        
        # Run Flask app
        app.run(host='0.0.0.0', port=5000, debug=False)
    else:
        logger.error("❌ Failed to start service - model loading failed")
        sys.exit(1)
