# 🎵 Cloud-Based Music Classification System

A complete cloud-based music classification system that automatically categorizes music as "Christian" or "Secular" using machine learning.

## 🏗️ Architecture

```
Android App → Extract Features → HTTP Request → Render.com Service → TensorFlow Model → Classification Result → Store Locally → Create Playlists
```

## 🚀 Quick Start

### 1. Deploy Cloud Service to Render.com

1. Copy your trained model to the service directory:
   ```bash
   cp /path/to/christian_music_classifier/models/improved_audio_classifier_random_forest.joblib ./models/
   ```

2. Deploy to Render.com:
   - Connect Git repository to Render
   - Create Web Service
   - Set Build Command: `pip install -r requirements.txt`
   - Set Start Command: `python cloud_classification_service.py`
   - Runtime: Python 3.11

3. Update Android app with your Render URL in `CloudClassificationService.kt`

### 2. Android App Features

- **Music Classification Screen**: Access via overflow menu
- **Cloud Service Health Check**: Verify service availability
- **Batch Classification**: Process multiple songs
- **Automatic Playlist Generation**: Creates "Christian Music" and "Secular Music" playlists
- **Manual Override**: Users can correct misclassifications

## 📊 API Endpoints

- `GET /health` - Service health check
- `POST /classify` - Classify single song
- `POST /batch_classify` - Classify multiple songs
- `GET /model_info` - Model information

## 🎯 Usage

1. Open Music Classification from overflow menu
2. Check service health
3. Classify songs using "Classify All"
4. Generate playlists automatically
5. Review and correct classifications

## 🔧 Technical Details

- **Model Accuracy**: ~84% overall
- **Features**: 50+ audio features extracted
- **Database**: Room database with migration
- **UI**: Material Design 3 components

## 🐛 Troubleshooting

- Check Render.com service status
- Verify model file location
- Review service logs
- Test network connectivity

See `CLOUD_SERVICE_README.md` for detailed deployment instructions.
