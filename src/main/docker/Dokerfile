FROM java:8

# Set the location of the verticles
ENV ANALYTICS_HOME /analytics

EXPOSE 8080

# Copy your fat jar to the container
COPY analytics.tgz /

# Launch the verticle
WORKDIR $ANALYTICS_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["$ANALYTICS_HOME/run-bun/analytics.sh"]

