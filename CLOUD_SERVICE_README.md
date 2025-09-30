# Cloud Music Classification Service

A Flask-based cloud service for classifying music as Christian or Secular using machine learning. This service is designed to be deployed on Render.com and integrated with the BoomingMusic Android app.

## Features

- **Audio Classification**: Classify music as Christian or Secular with confidence scores
- **Batch Processing**: Process multiple songs in a single request
- **RESTful API**: Clean HTTP endpoints for easy integration
- **Health Monitoring**: Built-in health check endpoint
- **Error Handling**: Comprehensive error handling and logging

## API Endpoints

### Health Check
```
GET /health
```
Returns service status and model loading information.

### Single Song Classification
```
POST /classify
Content-Type: application/json

{
    "audio_data": [list of audio samples],
    "sample_rate": 22050,
    "song_id": "optional_song_identifier"
}
```

### Batch Classification
```
POST /batch_classify
Content-Type: application/json

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
```

### Model Information
```
GET /model_info
```
Returns detailed information about the loaded model.

## Deployment on Render.com

### Prerequisites
1. A Render.com account
2. The trained model from `christian_music_classifier` directory
3. Git repository with the service code

### Steps

1. **Prepare the Model**:
   - Copy the trained model file (`improved_audio_classifier_random_forest.joblib`) to the service directory
   - Ensure the model is in the `models/` subdirectory

2. **Deploy to Render**:
   - Connect your Git repository to Render
   - Create a new Web Service
   - Set the following configuration:
     - **Build Command**: `pip install -r requirements.txt`
     - **Start Command**: `python cloud_classification_service.py`
     - **Runtime**: Python 3.11
     - **Instance Type**: Starter (512MB) or higher

3. **Environment Variables** (optional):
   - `MODEL_PATH`: Custom path to the model file
   - `LOG_LEVEL`: Logging level (default: INFO)

### Model Integration

The service automatically searches for the trained model in these locations:
1. `models/improved_audio_classifier_random_forest.joblib`
2. `../christian_music_classifier/models/improved_audio_classifier_random_forest.joblib`
3. `/opt/render/project/src/christian_music_classifier/models/improved_audio_classifier_random_forest.joblib`

## Response Format

### Successful Classification
```json
{
    "prediction": "Christian",
    "confidence": 0.87,
    "probabilities": {
        "christian": 0.87,
        "secular": 0.13
    },
    "success": true,
    "song_id": "song_identifier"
}
```

### Error Response
```json
{
    "prediction": "unknown",
    "confidence": 0.0,
    "probabilities": {
        "christian": 0.5,
        "secular": 0.5
    },
    "success": false,
    "error": "Error description"
}
```

## Performance

- **Processing Speed**: ~2-5 seconds per song
- **Batch Limit**: Maximum 50 songs per batch request
- **Memory Usage**: ~200-400MB (depending on instance size)
- **Accuracy**: ~84% overall accuracy on test data

## Monitoring

The service includes comprehensive logging and monitoring:
- Request/response logging
- Error tracking with stack traces
- Performance metrics
- Health check endpoint for uptime monitoring

## Security

- CORS enabled for Android app integration
- Input validation and sanitization
- Error handling prevents information leakage
- Rate limiting recommended for production use

## Troubleshooting

### Common Issues

1. **Model Not Found**:
   - Ensure the model file is in the correct location
   - Check file permissions
   - Verify the model file is not corrupted

2. **Memory Issues**:
   - Upgrade to a larger instance type
   - Reduce batch size
   - Check for memory leaks in audio processing

3. **Audio Processing Errors**:
   - Verify audio data format (should be float32 array)
   - Check sample rate compatibility
   - Ensure audio data is not empty

### Logs

Check Render.com logs for detailed error information:
- Build logs: Installation and startup issues
- Runtime logs: Request processing and errors
- Health check logs: Service status monitoring

## Integration with Android App

This service is designed to work with the BoomingMusic Android app:

1. **Audio Feature Extraction**: Extract features locally on Android
2. **HTTP Requests**: Send features to this cloud service
3. **Result Storage**: Store classification results in local database
4. **Playlist Generation**: Create Christian/Secular playlists based on results

See the Android integration code for complete implementation details.
