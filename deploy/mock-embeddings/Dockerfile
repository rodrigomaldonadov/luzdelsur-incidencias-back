FROM python:3.12-alpine

WORKDIR /app
COPY main.py .

RUN adduser -D -H app
USER app

EXPOSE 8000
CMD ["python", "main.py"]
